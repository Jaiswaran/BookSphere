with open('app/src/main/java/com/example/data/repository/BookSphereRepository.kt', 'r') as f:
    content = f.read()

seed_func = """
    suspend fun seedInitialDataIfEmpty() {
        if (bookDao.getBookCount() == 0) {
            val initialBooks = listOf(SampleData.sampleCelestialCartographer)
            bookDao.insertBooks(initialBooks)
        }
        if (userCredentialDao.getCredentialCount() == 0) {
            val defaultAuthor = UserCredentialEntity(
                username = "elena.rostova",
                phoneNumber = "+91 98765 43210",
                email = "elena.rostova@booksphere.lit",
                passwordOrToken = "••••••••••••",
                role = "AUTHOR",
                authMethod = "credentials",
                isActive = true
            )
            userCredentialDao.insertCredential(defaultAuthor)
        }
    }
"""

content = content.replace("    /**\n     * Stores a new published book by an author", seed_func + "\n    /**\n     * Stores a new published book by an author")

with open('app/src/main/java/com/example/data/repository/BookSphereRepository.kt', 'w') as f:
    f.write(content)
