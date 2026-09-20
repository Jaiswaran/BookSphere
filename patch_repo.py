import re

with open('app/src/main/java/com/example/data/repository/BookSphereRepository.kt', 'r') as f:
    content = f.read()

imports = """import kotlinx.coroutines.flow.Flow
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import android.util.Log"""

content = content.replace("import kotlinx.coroutines.flow.Flow", imports)

class_body_start = "class BookSphereRepository(private val database: AppDatabase) {"
class_body_replacement = """class BookSphereRepository(private val database: AppDatabase) {
    private val bookDao = database.bookDao()
    private val userCredentialDao = database.userCredentialDao()
    private val firestore = FirebaseFirestore.getInstance()

    val allPublishedBooks: Flow<List<BookEntity>> = bookDao.getAllPublishedBooks()
    val allCredentials: Flow<List<UserCredentialEntity>> = userCredentialDao.getAllCredentials()
    val activeCredential: Flow<UserCredentialEntity?> = userCredentialDao.getActiveCredential()

    init {
        // Listen to Firestore for any new books and insert them locally
        firestore.collection("books").addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("Firestore", "Listen failed.", e)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val books = snapshot.documents.mapNotNull { doc ->
                    try {
                        BookEntity(
                            id = doc.getString("id") ?: return@mapNotNull null,
                            title = doc.getString("title") ?: "",
                            author = doc.getString("author") ?: "",
                            genre = doc.getString("genre") ?: "",
                            description = doc.getString("description") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            coverUrl = doc.getString("coverUrl") ?: "",
                            pdfUri = doc.getString("pdfUri"),
                            language = doc.getString("language") ?: "English",
                            totalPages = doc.getLong("totalPages")?.toInt() ?: 280,
                            previewPages = doc.getLong("previewPages")?.toInt() ?: 3,
                            samplePagesCount = doc.getLong("samplePagesCount")?.toInt() ?: 20,
                            contentPagesJson = doc.getString("contentPagesJson") ?: "[]",
                            status = doc.getString("status") ?: "Published",
                            copiesSold = doc.getLong("copiesSold")?.toInt() ?: 0,
                            netEarned = doc.getDouble("netEarned") ?: 0.0,
                            rating = doc.getDouble("rating") ?: 5.0,
                            publishedAt = doc.getLong("publishedAt") ?: System.currentTimeMillis()
                        )
                    } catch (ex: Exception) {
                        null
                    }
                }
                
                // Insert fetched books to local Room DB
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    bookDao.insertBooks(books)
                }
            }
        }
    }
"""

content = re.sub(r'class BookSphereRepository.*?val activeCredential: Flow<UserCredentialEntity\?> = userCredentialDao\.getActiveCredential\(\)', class_body_replacement, content, flags=re.DOTALL)

publish_book = """
    /**
     * Stores a new published book by an author in the Room database and pushes to Firestore.
     */
    suspend fun publishBook(
        title: String,
        author: String,
        genre: String,
        price: Double,
        description: String,
        coverUrl: String = SampleData.sampleCelestialCartographer.coverUrl,
        pdfUri: String? = null,
        language: String = "English",
        totalPages: Int = 280,
        previewPages: Int = 3,
        isFree: Boolean = false
    ): BookEntity {
        val finalPrice = if (isFree) 0.0 else price
        val id = "work_${System.currentTimeMillis()}"
        val entity = BookEntity(
            id = id,
            title = title,
            author = author,
            genre = genre,
            price = finalPrice,
            description = description,
            coverUrl = coverUrl,
            pdfUri = pdfUri,
            language = language,
            totalPages = totalPages,
            previewPages = previewPages,
            samplePagesCount = previewPages,
            status = "Published",
            copiesSold = 0,
            netEarned = 0.0,
            rating = 5.0,
            publishedAt = System.currentTimeMillis()
        )
        bookDao.insertBook(entity)
        
        // Push to Firestore so everyone can see it
        try {
            val bookMap = hashMapOf(
                "id" to entity.id,
                "title" to entity.title,
                "author" to entity.author,
                "genre" to entity.genre,
                "price" to entity.price,
                "description" to entity.description,
                "coverUrl" to entity.coverUrl,
                "pdfUri" to entity.pdfUri,
                "language" to entity.language,
                "totalPages" to entity.totalPages,
                "previewPages" to entity.previewPages,
                "samplePagesCount" to entity.samplePagesCount,
                "contentPagesJson" to entity.contentPagesJson,
                "status" to entity.status,
                "copiesSold" to entity.copiesSold,
                "netEarned" to entity.netEarned,
                "rating" to entity.rating,
                "publishedAt" to entity.publishedAt
            )
            firestore.collection("books").document(id).set(bookMap, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e("Firestore", "Error publishing book to Firestore", e)
        }
        
        return entity
    }
"""

content = re.sub(r'    /\*\*.*?Stores a new published book.*?suspend fun publishBook.*?return entity\n    \}', publish_book, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/repository/BookSphereRepository.kt', 'w') as f:
    f.write(content)
