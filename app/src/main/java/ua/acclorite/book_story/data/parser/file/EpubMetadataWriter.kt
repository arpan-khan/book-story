package ua.acclorite.book_story.data.parser.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.UUID
import javax.inject.Inject
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.io.path.outputStream

private const val TAG = "EpubMetadataWriter"

class EpubMetadataWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun writeMetadata(
        fileUri: Uri,
        title: String?,
        subtitle: String?,
        author: String?,
        description: String?,
        coverImageUri: Uri?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            logI(TAG, "Writing metadata to $fileUri")

            // 1. Copy the SAF file to a local temporary file for modification
            val tempFile = File(context.cacheDir, "temp_epub_${UUID.randomUUID()}.epub")
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not open input stream for $fileUri")

            // 2. Open the ZIP FileSystem to modify the EPUB in-place
            val env = mapOf("create" to "false")
            val zipUri = URI.create("jar:file:${tempFile.absolutePath}")
            
            var modified = false

            FileSystems.newFileSystem(zipUri, env).use { fs ->
                // Find all .opf files in the root and META-INF directories (standard EPUB)
                // Actually we should read META-INF/container.xml to find the exact OPF path.
                val containerPath = fs.getPath("/META-INF/container.xml")
                var opfPathStr: String? = null
                
                if (Files.exists(containerPath)) {
                    val containerXml = containerPath.readText()
                    val containerDoc = Jsoup.parse(containerXml, Parser.xmlParser())
                    opfPathStr = containerDoc.select("rootfile").attr("full-path")
                }
                
                if (opfPathStr.isNullOrBlank()) {
                    // Fallback to searching for .opf
                    val rootDir = fs.getPath("/")
                    Files.walk(rootDir).use { stream ->
                        val opfFile = stream.filter { it.name.endsWith(".opf", ignoreCase = true) }.findFirst()
                        if (opfFile.isPresent) {
                            opfPathStr = opfFile.get().toString()
                        }
                    }
                }

                if (opfPathStr != null) {
                    val opfPath = fs.getPath(opfPathStr!!)
                    if (Files.exists(opfPath)) {
                        val opfContent = opfPath.readText()
                        val document = Jsoup.parse(opfContent, Parser.xmlParser())
                        
                        var opfChanged = false

                        // Update Title
                        if (title != null) {
                            val titleTags = document.select("metadata > dc|title").filter {
                                !it.hasAttr("opf:type") || it.attr("opf:type") == "main"
                            }
                            if (titleTags.isNotEmpty()) {
                                if (titleTags.first()?.text() != title) {
                                    titleTags.first()?.text(title)
                                    opfChanged = true
                                }
                            } else {
                                document.select("metadata").append("<dc:title>$title</dc:title>")
                                opfChanged = true
                            }
                        }

                        // Update Subtitle
                        if (subtitle != null) {
                            val subtitleTags = document.select("metadata > dc|title").filter {
                                it.attr("opf:type") == "subtitle"
                            }
                            if (subtitleTags.isNotEmpty()) {
                                if (subtitleTags.first()?.text() != subtitle) {
                                    subtitleTags.first()?.text(subtitle)
                                    opfChanged = true
                                }
                            } else if (subtitle.isNotBlank()) {
                                document.select("metadata").append("<dc:title opf:type=\"subtitle\">$subtitle</dc:title>")
                                opfChanged = true
                            }
                        }

                        // Update Author
                        if (author != null) {
                            val authorTags = document.select("metadata > dc|creator")
                            if (authorTags.isNotEmpty()) {
                                if (authorTags.first()?.text() != author) {
                                    authorTags.first()?.text(author)
                                    opfChanged = true
                                }
                            } else {
                                document.select("metadata").append("<dc:creator>$author</dc:creator>")
                                opfChanged = true
                            }
                        }

                        // Update Description
                        val descVal = description ?: ""
                        val descTags = document.select("metadata > dc|description")
                        if (descTags.isNotEmpty()) {
                            if (descTags.first()?.text() != descVal) {
                                descTags.first()?.text(descVal)
                                opfChanged = true
                            }
                        } else if (descVal.isNotBlank()) {
                            document.select("metadata").append("<dc:description>$descVal</dc:description>")
                            opfChanged = true
                        }

                        // Cover Image Replacement
                        if (coverImageUri != null && coverImageUri.toString().isNotEmpty()) {
                            // Find cover id from meta tag
                            val coverMeta = document.select("metadata > meta[name=cover]").first()
                            val coverId = coverMeta?.attr("content")
                            
                            if (coverId != null) {
                                val coverItem = document.select("manifest > item[id=$coverId]").first()
                                val coverHref = coverItem?.attr("href")
                                
                                if (coverHref != null) {
                                    // Calculate relative path to OPF
                                    val opfParent = opfPath.parent
                                    val coverImgPath = if (opfParent != null) {
                                        opfParent.resolve(coverHref)
                                    } else {
                                        fs.getPath(coverHref)
                                    }
                                    
                                    if (Files.exists(coverImgPath)) {
                                        context.contentResolver.openInputStream(coverImageUri)?.use { coverIn ->
                                            coverImgPath.outputStream().use { coverOut ->
                                                coverIn.copyTo(coverOut)
                                            }
                                        }
                                        modified = true
                                    }
                                }
                            }
                        }

                        if (opfChanged) {
                            // Write back OPF
                            // Jsoup might change formatting, but we output as XML
                            document.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                            opfPath.writeText(document.outerHtml())
                            modified = true
                        }
                    }
                }
            }

            // 3. Write back to SAF URI if modified
            if (modified) {
                context.contentResolver.openOutputStream(fileUri, "wt")?.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not open output stream for $fileUri")
                logI(TAG, "Successfully wrote metadata to $fileUri")
            }

            // 4. Clean up temp file
            tempFile.delete()
            
            true
        } catch (e: Exception) {
            logE(TAG, "Error writing metadata: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
