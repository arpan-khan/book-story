package ua.acclorite.book_story.domain.use_case.book

import android.net.Uri
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.parser.file.EpubMetadataWriter
import ua.acclorite.book_story.domain.model.library.Book
import javax.inject.Inject

private const val TAG = "WriteEpubMetadata"

class WriteEpubMetadataUseCase @Inject constructor(
    private val epubMetadataWriter: EpubMetadataWriter,
    private val getFileFromBookUseCase: GetFileFromBookUseCase
) {

    suspend operator fun invoke(bookId: Int, title: String?, subtitle: String?, author: String?, description: String?, coverImageUri: Uri?) {
        logI(TAG, "Starting to write EPUB metadata for book [$bookId].")
        
        try {
            val file = getFileFromBookUseCase(bookId)
            
            if (file != null && file.name.endsWith(".epub", ignoreCase = true)) {
                val success = epubMetadataWriter.writeMetadata(
                    fileUri = Uri.parse(file.uri),
                    title = title,
                    subtitle = subtitle,
                    author = author,
                    description = description,
                    coverImageUri = coverImageUri
                )
                
                if (success) {
                    logI(TAG, "Successfully wrote EPUB metadata for book [$bookId].")
                } else {
                    logE(TAG, "Failed to write EPUB metadata for book [$bookId].")
                }
            } else {
                logI(TAG, "Book [$bookId] is not an EPUB file, skipping metadata write.")
            }
        } catch (e: Exception) {
            logE(TAG, "Could not write EPUB metadata for book [$bookId] with error: ${e.message}")
        }
    }
}
