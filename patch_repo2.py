import re

with open('app/src/main/java/com/example/data/repository/BookSphereRepository.kt', 'r') as f:
    content = f.read()

# Add missing import for launch
content = content.replace('import android.util.Log', 'import android.util.Log\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.Dispatchers')

# Fix class signature
content = content.replace('class BookSphereRepository(private val database: AppDatabase) {', 'class BookSphereRepository(private val bookDao: BookDao, private val userCredentialDao: UserCredentialDao) {')
content = content.replace('    private val bookDao = database.bookDao()\n', '')
content = content.replace('    private val userCredentialDao = database.userCredentialDao()\n', '')

# Replace GlobalScope with CoroutineScope(Dispatchers.IO)
content = content.replace('kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {', 'CoroutineScope(Dispatchers.IO).launch {')

# Also, the seedInitialDataIfEmpty is missing because my previous regex replaced it.
# Let's restore the full content from original if possible? I don't have git.
