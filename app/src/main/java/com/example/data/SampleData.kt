package com.example.data

import com.example.model.*

object SampleData {
    const val LOGO_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuAPxsDylFNzBNBHfJWl2GBq4fsVRVzUwx8yn5OfpnR8bs_WFOpny8qCenKGE_MEMBMHlHTJRViGmS7cP9Ow8I_JrS5MqHjzG88HH1NSvJYGU_AYOI8ipAOwjwCok0qBG5Z2RvK9RbtUa6YaraWoWDrLqDGaatLOsRvKOPloa59-TPnNqidVQ7W173r992yrDVoRESFFrE91mfiWKwnWtJ634MrVeX5B_qaPxRsRujr8yU3GHUOvHkLN"
    const val AUTHOR_AVATAR = "https://lh3.googleusercontent.com/aida-public/AB6AXuCev1HvMLVpVmXZ55glfupYklmea-rnhzteh0q9MzTR6IgY0lxVpWvj9zZBtXGcqnoHbmcaUns3pIgz4XruTt9FdKonNT0K9NxwnNyE9uVNe3Um841VGyvmPD6cNtGriePjxSYrpltpgoAhZDeottqeMxHPM5l9qJf5zEX0M7Vybjr3TB8kO-dBBPriXYMGKOfv3ZesdLoV5QRmS1oRAyUYqRifQHzu8tXBb1G36LIoT6h9EGTJGqCt"

    val sampleCelestialCartographer = Book(
        id = "celestial_cartographer",
        title = "The Celestial Cartographer",
        author = "Julian Vance",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBQLrmtlq82ZluXYXcd9NqUa7_0nU_URwIaHjoczlreZK0xPW341rZd13kK-1J7yooZbzKn5uNlodiTWqIk7geJ134Aqr5u3_5Wre-_RB7HBWSF0rTimh7N9JrlnzM1wruY7-oHQGLtDPK-5yLCcHDRc-r2uyi0kdMofhky8Fnq5jZmT_u72PsbyXIgTFjBONcFoKOXSr2Lm48jSz3pJ-h4QeHBURaKq_e2TSANfOpZ6nVnQPGOcbVK",
        price = 399.0,
        rating = 4.96,
        reviewCount = 342,
        genre = "Speculative Fiction",
        description = "In an alternate 17th-century Prague, an exiled astronomer uncovers a hidden celestial atlas that charts constellations yet unborn. A luminous meditation on destiny, mathematics, and longing.",
        totalPages = 340,
        samplePagesCount = 20,
        authorBio = "Julian Vance is an independent historical fantasy novelist based in Edinburgh. Published exclusively through BookSphere's Fair Royalties model.",
        samplePages = listOf(
            SamplePage(
                pageNumber = 1,
                chapterTitle = "CHAPTER I — THE MERIDIAN OF GLASS",
                dropCapLetter = "I",
                firstSentenceRemainder = "n the twilight hours before the bells of the Old Town began their tolling, Master Kaelen drew the heavy velvet curtains and set the brass astrolabe onto the worn maple desk.",
                paragraphs = listOf(
                    "The instrument was older than the kingdom itself, engraved with astrological glyphs that had long fallen out of common astronomical parlance. Its pierced rete was cold against his fingertips.",
                    "Beneath the high rib vaults of the scriptorium, tallow candles sputtered in the autumn draught that crept between the leaded windowpanes. Outside, the Vltava River carried fragments of river mist through the arches of the stone bridge, muffling the footsteps of early morning guild watchmen.",
                    "For twenty-seven years, Kaelen had charted the movements of the planets for the Imperial Court. But tonight was different. Tonight the ephemeris tables bore an irregularity that no Euclidean geometry could dismiss: a seventh luminous body had stationed itself inside the cusp of the Cygnus constellation."
                ),
                footnote = "* Recorded in the Codex Vltavensis, preserved in the clandestine archive of the Guild of Glassmakers.",
                marginNote = "Note: Observe how the parallax shift anticipates the 1682 solstice."
            ),
            SamplePage(
                pageNumber = 2,
                chapterTitle = "CHAPTER I — (CONTINUED)",
                dropCapLetter = "H",
                firstSentenceRemainder = "e dipped his crow-quill into sepia gall and hesitated above the calfskin parchment.",
                paragraphs = listOf(
                    "\"To publish this discovery to the Chancery,\" he whispered to the empty chamber, \"is to invite the tribunal. To conceal it is to deny the heavens their rightful architecture.\"",
                    "A knock sounded at the heavy oaken door—three measured taps, followed by the distinctive metallic scrape of a signet ring. It was Morvath, the apprentice whose loyalty had survived the winter purges.",
                    "\"Master,\" the young man breathed as the bolt slid back, \"the night courier from Venice has arrived with the sealed folio. He says the glass prism ground in Murano cannot bear the refraction of starlight without producing chromatic shadows in the shape of letters.\"",
                    "Kaelen felt a sudden constriction in his chest. The rumors were true. The stars were not merely burning fires; they were an unbroken transcript waiting for an eye patient enough to read them."
                ),
                footnote = "Page 2 of 20 Sample Pages. Unlock all 340 pages instantly upon purchase.",
                marginNote = "Reader preview limit: First 2-3 pages provided freely by author permission."
            )
        )
    )

    val staffPickBook = Book(
        id = "atlas_forgotten_stars",
        title = "An Atlas of Forgotten Stars",
        author = "Elena Rostova",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB-cPew5GkXbFMCwQ8t3umj-6P0Xp9apOmth6quK88aCXzgckmAisrobQ01faTIh737irKQMGM4MKn06z7BhAbh5snfAI3GiQlKqEXXaR0ZIA1RdVSEsRs2xcT0lmZIg4_KfntsJzc8zMlCOnFR_Wa3swV63Bv0s5FRV0RV8Gj7A2gtbgPdI29NTMZ8IjBCpAb_SvIW6o9umYpzm-nUxmXs2_S_rtkCPh4hV4eofmQf6i_lpzUyqgd2",
        price = 599.0,
        rating = 4.98,
        reviewCount = 824,
        genre = "Cosmic Realism",
        description = "A poetic tapestry of cartography, forgotten navigators, and the lost constellations charted by Mediterranean sailors. Winner of the 2026 Independent Literary Guild Prize.",
        totalPages = 296,
        samplePagesCount = 20,
        samplePages = sampleCelestialCartographer.samplePages,
        authorBio = "Elena Rostova is a writer and cartographer who champions reader-first publishing."
    )

    val trendingBooks = listOf(
        sampleCelestialCartographer,
        Book(
            id = "quiet_echo_madrid",
            title = "A Quiet Echo in Madrid",
            author = "Sofia Alcantara",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAbREXm6WuoQfTSx4gwFSveQ804RzPvIA3wH4j5b90oGsgViZGiCvpqZN2UT1TS26iqtq9uyyq-GvSzXfkfiQp3CXJe-9bxFYL0hwOk_CuPAeHydE3yX4VSfMJeCBGDndEWdViVVu_C3zoXbMJoS4ATPJGXJ4xrZMP8RxrRQkkc_3M7gt5H3r43PBgStnxnB70v86eCSWHTdo_BQzotuOko20SpOhrfWP3_1ojcFiXkvxhE5COfYBBv",
            price = 499.0,
            rating = 4.91,
            reviewCount = 189,
            genre = "Historical Mystery",
            description = "During the turbulent summer of 1934, a young cello restorer discovers coded correspondence hidden inside an Antonio Stradivari instrument.",
            totalPages = 312,
            samplePagesCount = 18,
            samplePages = sampleCelestialCartographer.samplePages
        ),
        Book(
            id = "chronicles_sunken_isle",
            title = "Chronicles of the Sunken Isle",
            author = "Liam Thornfield",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB1fQTnZbo-G_8pVObAMBWRr6YR3soG09vy9aabYJpl7QAUbcVL9u_Cf5Jsa6nSjB-8Il3pS9n1x8te_NvH0t6-fDpC6p78mT7maK-dJeMc0yJke2_kwZNH-hkGyIAaH2ukhyA9ffMmkf3gPhMjCRusOyfgbiVRlnt6cUNSFsesaRjTfzC_cYfl03x_JTVvejfXgx88m4OLn7hhWvapa2vme-AOcEMCYgq5AfKNt4cl1otmUmhFd2J8",
            price = 449.0,
            rating = 4.88,
            reviewCount = 210,
            genre = "High Fantasy",
            description = "A mythical archipelagic kingdom submerges once every century, returning with forgotten technologies and dangerous tidal relics.",
            totalPages = 420,
            samplePagesCount = 25,
            samplePages = sampleCelestialCartographer.samplePages
        ),
        Book(
            id = "echoes_silk_road",
            title = "Echoes of the Silk Road",
            author = "Tariq Mansoor",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuC0sIHf_jcOtd7qQkG3KDifoGeq0g-Q5ztUjaTZws8e4N-WfskPBfw1ZWp06qyWI1ncfRo6GmXMTkRezar5amp4s0IJY9zNnuJJmRPSl8kHc0_o8-oLsgLEtpvd-0qbtXzwq3p3lWzV4fb6myIY7koeiPnKAET-kR741ciHCf6NALnXvbE_ToJL6LCoWjr3QZMLp3F5b9xL8Rt7mXTNEri8xH1-zl-2KpBHpGL7f9geh5lSwEWge0R9",
            price = 499.0,
            rating = 4.94,
            reviewCount = 156,
            genre = "Literary Travelogue",
            description = "A reflective, richly sensory pilgrimage along ancient caravan routes from Samarkand to Kashgar, recounting forgotten tea houses and poetic duels.",
            totalPages = 280,
            samplePagesCount = 15,
            samplePages = sampleCelestialCartographer.samplePages
        )
    )

    val libraryBooks = listOf(
        Book(
            id = "the_shadow_of_the_wind",
            title = "The Shadow of the Wind",
            author = "Carlos Ruiz Zafón",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBQLrmtlq82ZluXYXcd9NqUa7_0nU_URwIaHjoczlreZK0xPW341rZd13kK-1J7yooZbzKn5uNlodiTWqIk7geJ134Aqr5u3_5Wre-_RB7HBWSF0rTimh7N9JrlnzM1wruY7-oHQGLtDPK-5yLCcHDRc-r2uyi0kdMofhky8Fnq5jZmT_u72PsbyXIgTFjBONcFoKOXSr2Lm48jSz3pJ-h4QeHBURaKq_e2TSANfOpZ6nVnQPGOcbVK",
            price = 449.0,
            progress = 0.85f,
            progressText = "85% read",
            timeLeft = "42m left",
            badgeType = "notes",
            badgeValue = "12",
            isPurchased = true
        ),
        Book(
            id = "piranesi",
            title = "Piranesi",
            author = "Susanna Clarke",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAbREXm6WuoQfTSx4gwFSveQ804RzPvIA3wH4j5b90oGsgViZGiCvpqZN2UT1TS26iqtq9uyyq-GvSzXfkfiQp3CXJe-9bxFYL0hwOk_CuPAeHydE3yX4VSfMJeCBGDndEWdViVVu_C3zoXbMJoS4ATPJGXJ4xrZMP8RxrRQkkc_3M7gt5H3r43PBgStnxnB70v86eCSWHTdo_BQzotuOko20SpOhrfWP3_1ojcFiXkvxhE5COfYBBv",
            price = 499.0,
            progress = 0.24f,
            progressText = "24% read",
            timeLeft = "3h 10m left",
            isPurchased = true
        ),
        Book(
            id = "klara_and_the_sun",
            title = "Klara and the Sun",
            author = "Kazuo Ishiguro",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB1fQTnZbo-G_8pVObAMBWRr6YR3soG09vy9aabYJpl7QAUbcVL9u_Cf5Jsa6nSjB-8Il3pS9n1x8te_NvH0t6-fDpC6p78mT7maK-dJeMc0yJke2_kwZNH-hkGyIAaH2ukhyA9ffMmkf3gPhMjCRusOyfgbiVRlnt6cUNSFsesaRjTfzC_cYfl03x_JTVvejfXgx88m4OLn7hhWvapa2vme-AOcEMCYgq5AfKNt4cl1otmUmhFd2J8",
            price = 449.0,
            badgeType = "new",
            badgeValue = "0% • Unopened",
            isPurchased = true
        ),
        Book(
            id = "exhalation",
            title = "Exhalation",
            author = "Ted Chiang",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuC0sIHf_jcOtd7qQkG3KDifoGeq0g-Q5ztUjaTZws8e4N-WfskPBfw1ZWp06qyWI1ncfRo6GmXMTkRezar5amp4s0IJY9zNnuJJmRPSl8kHc0_o8-oLsgLEtpvd-0qbtXzwq3p3lWzV4fb6myIY7koeiPnKAET-kR741ciHCf6NALnXvbE_ToJL6LCoWjr3QZMLp3F5b9xL8Rt7mXTNEri8xH1-zl-2KpBHpGL7f9geh5lSwEWge0R9",
            price = 599.0,
            badgeType = "completed",
            badgeValue = "Completed Oct 14",
            isPurchased = true
        )
    )

    val currentVolume = Book(
        id = "midnight_library",
        title = "The Midnight Library",
        author = "Matt Haig",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB-cPew5GkXbFMCwQ8t3umj-6P0Xp9apOmth6quK88aCXzgckmAisrobQ01faTIh737irKQMGM4MKn06z7BhAbh5snfAI3GiQlKqEXXaR0ZIA1RdVSEsRs2xcT0lmZIg4_KfntsJzc8zMlCOnFR_Wa3swV63Bv0s5FRV0RV8Gj7A2gtbgPdI29NTMZ8IjBCpAb_SvIW6o9umYpzm-nUxmXs2_S_rtkCPh4hV4eofmQf6i_lpzUyqgd2",
        price = 449.0,
        progress = 0.52f,
        progressText = "52% finished",
        timeLeft = "1 hr 45 min left",
        totalPages = 304,
        isPurchased = true
    )

    val authorPublishedWorks = listOf(
        PublishedWork(
            id = "whispering_archive",
            title = "The Whispering Archive",
            price = 499.0,
            copiesSold = 824,
            rating = 4.9,
            netEarned = RoyaltyConfig.calculateAuthorNet(499.0, 824),
            status = "Published",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB-cPew5GkXbFMCwQ8t3umj-6P0Xp9apOmth6quK88aCXzgckmAisrobQ01faTIh737irKQMGM4MKn06z7BhAbh5snfAI3GiQlKqEXXaR0ZIA1RdVSEsRs2xcT0lmZIg4_KfntsJzc8zMlCOnFR_Wa3swV63Bv0s5FRV0RV8Gj7A2gtbgPdI29NTMZ8IjBCpAb_SvIW6o9umYpzm-nUxmXs2_S_rtkCPh4hV4eofmQf6i_lpzUyqgd2",
            genre = "Speculative Fiction"
        ),
        PublishedWork(
            id = "letters_northern_sea",
            title = "Letters to the Northern Sea",
            price = 399.0,
            copiesSold = 412,
            rating = 4.8,
            netEarned = RoyaltyConfig.calculateAuthorNet(399.0, 412),
            status = "Published",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAbREXm6WuoQfTSx4gwFSveQ804RzPvIA3wH4j5b90oGsgViZGiCvpqZN2UT1TS26iqtq9uyyq-GvSzXfkfiQp3CXJe-9bxFYL0hwOk_CuPAeHydE3yX4VSfMJeCBGDndEWdViVVu_C3zoXbMJoS4ATPJGXJ4xrZMP8RxrRQkkc_3M7gt5H3r43PBgStnxnB70v86eCSWHTdo_BQzotuOko20SpOhrfWP3_1ojcFiXkvxhE5COfYBBv",
            genre = "Poetry & Epistolary"
        ),
        PublishedWork(
            id = "obsidian_quill",
            title = "Draft: The Obsidian Quill",
            price = 449.0,
            copiesSold = 0,
            rating = 5.0,
            netEarned = 0.0,
            status = "Draft / In Review",
            coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB1fQTnZbo-G_8pVObAMBWRr6YR3soG09vy9aabYJpl7QAUbcVL9u_Cf5Jsa6nSjB-8Il3pS9n1x8te_NvH0t6-fDpC6p78mT7maK-dJeMc0yJke2_kwZNH-hkGyIAaH2ukhyA9ffMmkf3gPhMjCRusOyfgbiVRlnt6cUNSFsesaRjTfzC_cYfl03x_JTVvejfXgx88m4OLn7hhWvapa2vme-AOcEMCYgq5AfKNt4cl1otmUmhFd2J8",
            genre = "Dark Academia"
        )
    )

    val readerResonanceList = listOf(
        ReaderResonance(
            readerName = "Marcus R.",
            type = "Patron Tip",
            tipAmount = "₹350",
            comment = "\"Chapter 4 brought tears to my eyes. Thank you for self-publishing directly through BookSphere so I know my support reaches you!\"",
            rating = 5,
            timeAgo = "2h ago"
        ),
        ReaderResonance(
            readerName = "Dr. Samantha Lin",
            type = "Verified Review",
            comment = "\"Masterful world-building and breathtaking prose. The preview sample convinced me immediately—I purchased without a second thought.\"",
            rating = 5,
            timeAgo = "5h ago"
        ),
        ReaderResonance(
            readerName = "Clara Vance",
            type = "Shared Highlight",
            comment = "\"Between life and death there is a library... and within that library, the shelves go on forever.\" (Shared to 14 fellow readers)",
            rating = null,
            timeAgo = "1d ago"
        )
    )
}
