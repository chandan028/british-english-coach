package com.britishcoach.core.curriculum

import com.britishcoach.core.model.LessonCategory
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.PracticeItem
import com.britishcoach.core.model.PracticeMode
import com.britishcoach.core.model.Stage

/**
 * Built-in lesson content. It ships inside the app so that every lesson works
 * without the AI model or a network connection.
 *
 * Transcriptions use Standard Southern British English (SSBE) in the symbol set
 * used by most British learner dictionaries. Primary stress is marked ˈ and
 * secondary stress ˌ. Stress guides use capitals for the stressed syllable.
 */
object Curriculum {

    val lessons: List<LessonContent> by lazy { buildLessons() }

    fun byId(id: String): LessonContent? = lessons.firstOrNull { it.id == id }

    fun forStage(stage: Stage): List<LessonContent> = lessons.filter { it.stage == stage }

    private fun buildLessons(): List<LessonContent> {
        val list = mutableListOf<LessonContent>()
        var order = 0
        fun add(
            id: String,
            title: String,
            stage: Stage,
            category: LessonCategory,
            introduction: String,
            items: List<PracticeItem>,
            tips: List<String> = emptyList(),
            topic: String? = null,
            opening: String? = null,
        ) {
            order += 1
            list += LessonContent(
                id = id,
                title = title,
                stage = stage,
                category = category,
                lessonOrder = order,
                mode = if (topic != null) PracticeMode.GUIDED_CONVERSATION else PracticeMode.REPEAT_AFTER_COACH,
                introduction = introduction,
                items = items,
                tips = tips,
                conversationTopic = topic,
                openingQuestion = opening,
            )
        }

        // ---------------- Stage 1: Foundations ----------------
        add(
            "s1-greetings", "Hello and goodbye", Stage.FOUNDATIONS, LessonCategory.EXPRESSIONS,
            "Let's start with the phrases you'll use every single day. Listen first, then say each one at a comfortable pace.",
            listOf(
                PracticeItem("Hello, how are you?", "/həˈləʊ haʊ ɑː ˈjuː/", "he-LLO, how are YOU?"),
                PracticeItem("I'm fine, thank you.", "/aɪm ˈfaɪn ˈθæŋk juː/", "I'm FINE, THANK you."),
                PracticeItem("Nice to meet you.", "/ˈnaɪs tə ˈmiːt juː/", "NICE to MEET you."),
                PracticeItem("See you later.", "/ˈsiː juː ˈleɪtə/", "SEE you LA-ter."),
                PracticeItem("Have a lovely day.", "/ˈhæv ə ˈlʌvli ˈdeɪ/", "HAVE a LOVE-ly DAY."),
            ),
            listOf(
                "In British English the 'r' at the end of 'later' is not pronounced: /ˈleɪtə/.",
                "'To' is usually short and light in 'nice to meet you', close to /tə/.",
                "'Lovely' is a very common, friendly British word for something pleasant.",
            ),
        )
        add(
            "s1-th", "The two 'th' sounds", Stage.FOUNDATIONS, LessonCategory.SOUNDS,
            "English has two 'th' sounds. For both, the tip of your tongue rests lightly between your teeth and air flows over it.",
            listOf(
                PracticeItem("think", "/θɪŋk/", "THINK"),
                PracticeItem("three", "/θriː/", "THREE"),
                PracticeItem("this", "/ðɪs/", "THIS"),
                PracticeItem("mother", "/ˈmʌðə/", "MO-ther"),
                PracticeItem("Thank you for this.", "/ˈθæŋk juː fə ˈðɪs/", "THANK you for THIS."),
            ),
            listOf(
                "/θ/ in 'think' is voiceless: only air, no buzz in your throat.",
                "/ð/ in 'this' is voiced: put your hand on your throat and you should feel a buzz.",
                "If 'think' sounds like 'tink' or 'sink', check that your tongue really touches your teeth.",
            ),
        )
        add(
            "s1-vowel-length", "Short and long vowels", Stage.FOUNDATIONS, LessonCategory.SOUNDS,
            "Some English words differ only by a short or a long vowel. Listen carefully to the length and the shape of the mouth.",
            listOf(
                PracticeItem("ship, sheep", "/ʃɪp/, /ʃiːp/", "short SHIP, long SHEEP"),
                PracticeItem("full, fool", "/fʊl/, /fuːl/", "short FULL, long FOOL"),
                PracticeItem("cat, cart", "/kæt/, /kɑːt/", "short CAT, long CART"),
                PracticeItem("Sit in this seat.", "/ˈsɪt ɪn ðɪs ˈsiːt/", "SIT in this SEAT."),
            ),
            listOf(
                "The colon-like mark ː in /iː/ means the vowel is long.",
                "For /ɪ/ in 'ship' relax your mouth; for /iː/ in 'sheep' spread your lips a little, as if smiling.",
                "In British English 'cart' has a long open /ɑː/ and no 'r' sound.",
            ),
        )
        add(
            "s1-numbers-days", "Numbers and days", Stage.FOUNDATIONS, LessonCategory.VOCABULARY,
            "Numbers and days come up all the time. The stress in 'thirteen' and 'thirty' is what tells them apart.",
            listOf(
                PracticeItem("thirteen", "/ˌθɜːˈtiːn/", "thir-TEEN"),
                PracticeItem("thirty", "/ˈθɜːti/", "THIR-ty"),
                PracticeItem("Wednesday", "/ˈwenzdeɪ/", "WENZ-day"),
                PracticeItem("Tuesday", "/ˈtʃuːzdeɪ/", "TUES-day"),
                PracticeItem("My birthday is on the third of May.", "/maɪ ˈbɜːθdeɪ ɪz ɒn ðə ˈθɜːd əv ˈmeɪ/", "my BIRTH-day is on the THIRD of MAY."),
            ),
            listOf(
                "'-teen' numbers stress the end: thir-TEEN. '-ty' numbers stress the start: THIR-ty.",
                "'Wednesday' has two syllables. The first 'd' is silent.",
                "British speakers usually say dates as 'the third of May'.",
            ),
        )
        add(
            "s1-simple-sentences", "Simple sentences", Stage.FOUNDATIONS, LessonCategory.SENTENCES,
            "Now let's build complete sentences: a subject, a verb, and the rest. Say the whole sentence in one smooth line.",
            listOf(
                PracticeItem("I work in an office.", "/aɪ ˈwɜːk ɪn ən ˈɒfɪs/", "I WORK in an OF-fice."),
                PracticeItem("She has two brothers.", "/ʃi hæz ˈtuː ˈbrʌðəz/", "she has TWO BRO-thers."),
                PracticeItem("We are learning English.", "/wi ə ˈlɜːnɪŋ ˈɪŋɡlɪʃ/", "we are LEARN-ing ENG-lish."),
                PracticeItem("The train leaves at nine.", "/ðə ˈtreɪn ˈliːvz ət ˈnaɪn/", "the TRAIN LEAVES at NINE."),
            ),
            listOf(
                "With he, she and it, the present simple verb takes -s: 'she has', 'it leaves'.",
                "Small words like 'an', 'are' and 'at' are usually said quickly and softly.",
            ),
        )
        add(
            "s1-everyday-expressions", "Everyday British expressions", Stage.FOUNDATIONS, LessonCategory.EXPRESSIONS,
            "These are polite, everyday phrases you'll hear all over Britain. Politeness matters a lot, so 'please' and 'sorry' appear often.",
            listOf(
                PracticeItem("Could I have a cup of tea, please?", "/kəd aɪ ˈhæv ə ˈkʌp əv ˈtiː ˈpliːz/", "could I HAVE a CUP of TEA, PLEASE?"),
                PracticeItem("Excuse me, where is the station?", "/ɪkˈskjuːz miː ˈweər ɪz ðə ˈsteɪʃn/", "ex-CUSE me, WHERE is the STA-tion?"),
                PracticeItem("Sorry, could you say that again?", "/ˈsɒri kəd juː ˈseɪ ðæt əˈɡen/", "SOR-ry, could you SAY that a-GAIN?"),
                PracticeItem("Cheers, that's very kind of you.", "/tʃɪəz ðæts ˈveri ˈkaɪnd əv juː/", "CHEERS, that's VE-ry KIND of you."),
            ),
            listOf(
                "'Cheers' is a casual British way of saying thank you, not only a toast.",
                "'Again' is often /əˈɡen/ in British English.",
                "'Could I...?' sounds more polite than 'Can I...?' when asking for something.",
            ),
        )

        // ---------------- Stage 2: Pronunciation ----------------
        add(
            "s2-schwa", "The schwa sound /ə/", Stage.PRONUNCIATION, LessonCategory.VOWELS,
            "The schwa is the most common sound in English. It's a short, relaxed 'uh' found in unstressed syllables.",
            listOf(
                PracticeItem("about", "/əˈbaʊt/", "a-BOUT"),
                PracticeItem("banana", "/bəˈnɑːnə/", "ba-NA-na"),
                PracticeItem("teacher", "/ˈtiːtʃə/", "TEA-cher"),
                PracticeItem("water", "/ˈwɔːtə/", "WA-ter"),
                PracticeItem("A cup of tea.", "/ə ˈkʌp əv ˈtiː/", "a CUP of TEA."),
            ),
            listOf(
                "The schwa is never stressed. Keep it short and let your mouth relax.",
                "British English is non-rhotic: the 'r' in 'teacher' and 'water' is silent unless a vowel follows.",
            ),
        )
        add(
            "s2-stress-two", "Stress in two-syllable words", Stage.PRONUNCIATION, LessonCategory.WORD_STRESS,
            "Stress means one syllable is louder, longer and a little higher. Some words change meaning when the stress moves.",
            listOf(
                PracticeItem("a present (noun)", "/ˈpreznt/", "PRE-sent"),
                PracticeItem("to present (verb)", "/prɪˈzent/", "pre-SENT"),
                PracticeItem("a record (noun)", "/ˈrekɔːd/", "RE-cord"),
                PracticeItem("to record (verb)", "/rɪˈkɔːd/", "re-CORD"),
                PracticeItem("hotel", "/həʊˈtel/", "ho-TEL"),
            ),
            listOf(
                "Many two-syllable nouns stress the first syllable and many verbs stress the second.",
                "The unstressed syllable often becomes weaker, so 'pre' in 'present' (verb) sounds like /prɪ/.",
            ),
        )
        add(
            "s2-stress-long", "Stress in longer words", Stage.PRONUNCIATION, LessonCategory.WORD_STRESS,
            "In longer words the stress can move as the word grows. Listen for the strongest syllable each time.",
            listOf(
                PracticeItem("photograph", "/ˈfəʊtəɡrɑːf/", "PHO-to-graph"),
                PracticeItem("photographer", "/fəˈtɒɡrəfə/", "pho-TO-gra-pher"),
                PracticeItem("photographic", "/ˌfəʊtəˈɡræfɪk/", "pho-to-GRA-phic"),
                PracticeItem("comfortable", "/ˈkʌmftəbl/", "COMF-ta-ble"),
                PracticeItem("vegetable", "/ˈvedʒtəbl/", "VEG-ta-ble"),
                PracticeItem("development", "/dɪˈveləpmənt/", "de-VEL-op-ment"),
            ),
            listOf(
                "Words ending in -ic usually stress the syllable just before it: photo-GRA-phic.",
                "'Comfortable' and 'vegetable' usually have three syllables in natural speech, not four.",
            ),
        )
        add(
            "s2-v-w", "V and W", Stage.PRONUNCIATION, LessonCategory.CONSONANTS,
            "For /v/ your top teeth touch your bottom lip. For /w/ your lips make a small round circle and don't touch your teeth.",
            listOf(
                PracticeItem("vest, west", "/vest/, /west/", "VEST, WEST"),
                PracticeItem("vine, wine", "/vaɪn/, /waɪn/", "VINE, WINE"),
                PracticeItem("very well", "/ˈveri ˈwel/", "VE-ry WELL"),
                PracticeItem("We will visit Wales in the winter.", "/wi wɪl ˈvɪzɪt ˈweɪlz ɪn ðə ˈwɪntə/", "we will VI-sit WALES in the WIN-ter."),
            ),
            listOf(
                "Try a mirror: for /v/ you should see your teeth on your lip; for /w/ you should see rounded lips.",
                "/v/ is voiced, so you'll feel a light buzz.",
            ),
        )
        add(
            "s2-final-consonants", "Final consonants and -ed endings", Stage.PRONUNCIATION, LessonCategory.CONSONANTS,
            "English words often end in consonant clusters. Finishing the last sound clearly helps people understand you, especially for past tense verbs.",
            listOf(
                PracticeItem("worked", "/wɜːkt/", "WORKT (one syllable)"),
                PracticeItem("asked", "/ɑːskt/", "ASKT (one syllable)"),
                PracticeItem("wanted", "/ˈwɒntɪd/", "WAN-tid (two syllables)"),
                PracticeItem("friends, months", "/frendz/, /mʌnθs/", "FRENDZ, MONTHS"),
                PracticeItem("She walked to the shops.", "/ʃi ˈwɔːkt tə ðə ˈʃɒps/", "she WALKED to the SHOPS."),
            ),
            listOf(
                "-ed sounds like /t/ after voiceless sounds (worked), /d/ after voiced sounds (played) and /ɪd/ after t or d (wanted).",
                "Only -ed after t or d adds an extra syllable.",
                "The 'l' in 'walked' is silent: /wɔːkt/.",
            ),
        )
        add(
            "s2-l-r", "L and R", Stage.PRONUNCIATION, LessonCategory.CONSONANTS,
            "For /l/ the tip of your tongue touches the ridge behind your top teeth. For /r/ the tongue curls back slightly and doesn't touch anything.",
            listOf(
                PracticeItem("light, right", "/laɪt/, /raɪt/", "LIGHT, RIGHT"),
                PracticeItem("long, wrong", "/lɒŋ/, /rɒŋ/", "LONG, WRONG"),
                PracticeItem("bottle", "/ˈbɒtl/", "BOT-tle"),
                PracticeItem("The red lorry is really late.", "/ðə ˈred ˈlɒri ɪz ˈrɪəli ˈleɪt/", "the RED LOR-ry is REAL-ly LATE."),
            ),
            listOf(
                "British English uses a 'dark' L at the end of words like 'bottle' and 'feel', with the back of the tongue raised.",
                "In British English /r/ is only pronounced before a vowel sound: 'red' yes, 'car' no.",
            ),
        )
        add(
            "s2-diphthongs", "Gliding vowels", Stage.PRONUNCIATION, LessonCategory.VOWELS,
            "Some vowels glide from one position to another. The British 'oh' sound /əʊ/ starts from a relaxed schwa and moves towards a rounded /ʊ/.",
            listOf(
                PracticeItem("go home", "/ɡəʊ ˈhəʊm/", "go HOME"),
                PracticeItem("Don't go home alone.", "/ˈdəʊnt ɡəʊ ˈhəʊm əˈləʊn/", "DON'T go HOME a-LONE."),
                PracticeItem("Take a train to Wales.", "/ˈteɪk ə ˈtreɪn tə ˈweɪlz/", "TAKE a TRAIN to WALES."),
                PracticeItem("How about now?", "/ˈhaʊ əbaʊt ˈnaʊ/", "HOW a-bout NOW?"),
            ),
            listOf(
                "/eɪ/ in 'train' glides from /e/ towards /ɪ/. Avoid a flat single 'e' sound.",
                "Let the glide happen smoothly, without a pause in the middle.",
            ),
        )

        // ---------------- Stage 3: Everyday conversation ----------------
        add(
            "s3-introductions", "Introducing yourself", Stage.EVERYDAY, LessonCategory.CONVERSATION,
            "Let's practise talking about yourself. First repeat a few useful phrases, then we'll have a short conversation.",
            listOf(
                PracticeItem("Let me introduce myself.", "/ˈlet miː ˌɪntrəˈdjuːs maɪˈself/", "LET me in-tro-DUCE my-SELF."),
                PracticeItem("I'm originally from a small town.", null, "I'm o-RI-gin-al-ly from a SMALL TOWN."),
                PracticeItem("At the moment I'm working as an engineer.", null, "at the MO-ment I'm WORK-ing as an en-gi-NEER."),
                PracticeItem("In my free time I enjoy reading.", null, "in my FREE TIME I en-JOY READ-ing."),
            ),
            listOf("'At the moment' is a natural British way to say 'currently'."),
            topic = "Introducing yourself",
            opening = "Hello! I'd love to get to know you a little. Could you tell me where you're from?",
        )
        add(
            "s3-questions", "Asking questions", Stage.EVERYDAY, LessonCategory.INTONATION,
            "Your voice tells people whether you're asking or telling. Wh- questions usually fall at the end; yes/no questions often rise.",
            listOf(
                PracticeItem("Where do you work?", null, "WHERE do you WORK? (voice falls)"),
                PracticeItem("Do you like coffee?", null, "do you LIKE COF-fee? (voice rises)"),
                PracticeItem("What did you do at the weekend?", null, "WHAT did you DO at the WEEK-end? (voice falls)"),
                PracticeItem("You're coming tomorrow, aren't you?", null, "you're COM-ing to-MOR-row, AREN'T you? (falls when you expect yes)"),
            ),
            listOf("British English says 'at the weekend'; American English usually says 'on the weekend'."),
            topic = "Getting to know someone",
            opening = "Let's swap roles: this time you ask me the questions. What would you like to know about me?",
        )
        add(
            "s3-shopping", "Shopping", Stage.EVERYDAY, LessonCategory.CONVERSATION,
            "Let's practise a trip to the shops. These phrases will help you ask about prices and pay politely.",
            listOf(
                PracticeItem("How much is this jumper?", "/ˈhaʊ ˈmʌtʃ ɪz ðɪs ˈdʒʌmpə/", "how MUCH is this JUM-per?"),
                PracticeItem("I'm just looking, thanks.", null, "I'm JUST LOOK-ing, THANKS."),
                PracticeItem("Could I pay by card?", null, "could I PAY by CARD?"),
                PracticeItem("Do you have this in a medium?", null, "do you HAVE this in a ME-di-um?"),
            ),
            listOf(
                "A 'jumper' in British English is a 'sweater' in American English.",
                "Shop assistants often say 'Are you alright there?' meaning 'Can I help you?'.",
            ),
            topic = "Shopping for clothes",
            opening = "Welcome in! Are you looking for anything in particular today?",
        )
        add(
            "s3-travel", "Travelling by train", Stage.EVERYDAY, LessonCategory.CONVERSATION,
            "Trains are a big part of British life. Let's practise buying a ticket and asking for directions at the station.",
            listOf(
                PracticeItem("A return ticket to Oxford, please.", null, "a re-TURN TICK-et to OX-ford, PLEASE."),
                PracticeItem("Which platform does it leave from?", null, "WHICH PLAT-form does it LEAVE from?"),
                PracticeItem("How long does the journey take?", null, "how LONG does the JOUR-ney TAKE?"),
                PracticeItem("Is this seat taken?", null, "is this SEAT TA-ken?"),
            ),
            listOf("A 'return' ticket is a round-trip ticket; a 'single' is one-way."),
            topic = "At the train station",
            opening = "Good morning. Where would you like to travel to today?",
        )
        add(
            "s3-experiences", "Describing experiences", Stage.EVERYDAY, LessonCategory.CONVERSATION,
            "Talking about what you did is a great way to practise the past tense. Remember to finish those -ed endings.",
            listOf(
                PracticeItem("Last weekend I went to the seaside.", null, "LAST WEEK-end I WENT to the SEA-side."),
                PracticeItem("We stayed in a lovely little cottage.", null, "we STAYED in a LOVE-ly LIT-tle COT-tage."),
                PracticeItem("The best part was the view.", null, "the BEST PART was the VIEW."),
                PracticeItem("I'd definitely go back.", null, "I'd DEF-in-ite-ly GO BACK."),
            ),
            listOf("Use the past simple for finished events: 'I went', 'we stayed', not 'I have gone yesterday'."),
            topic = "A recent weekend or trip",
            opening = "So, tell me about something nice you did recently. Where did you go?",
        )
        add(
            "s3-cafe", "At a café or restaurant", Stage.EVERYDAY, LessonCategory.CONVERSATION,
            "Ordering food is a perfect everyday practice. We'll use polite British phrases for ordering and paying.",
            listOf(
                PracticeItem("Could I have the soup, please?", null, "could I HAVE the SOUP, PLEASE?"),
                PracticeItem("I'll have the same, thanks.", null, "I'll HAVE the SAME, THANKS."),
                PracticeItem("Could we have the bill, please?", null, "could we HAVE the BILL, PLEASE?"),
                PracticeItem("Is service included?", null, "is SER-vice in-CLU-ded?"),
            ),
            listOf("In Britain you ask for 'the bill'; in American English it's usually 'the check'."),
            topic = "Ordering at a café",
            opening = "Hiya, are you ready to order, or would you like a few more minutes?",
        )

        // ---------------- Stage 4: Fluency and professional English ----------------
        add(
            "s4-sentence-stress", "Sentence stress", Stage.PROFESSIONAL, LessonCategory.SENTENCE_STRESS,
            "In a sentence we stress the words that carry meaning and say the small grammar words more quickly. This gives English its rhythm.",
            listOf(
                PracticeItem("I need to finish the report by Friday.", null, "I NEED to FIN-ish the re-PORT by FRI-day."),
                PracticeItem("Could you send me the latest version?", null, "could you SEND me the LAT-est VER-sion?"),
                PracticeItem("We've made a lot of progress this week.", null, "we've MADE a LOT of PRO-gress this WEEK."),
            ),
            listOf(
                "Stressed words are usually nouns, main verbs, adjectives and adverbs.",
                "Words like 'to', 'the', 'of' and 'a' are usually weak and quick.",
            ),
        )
        add(
            "s4-storytelling", "Telling a story", Stage.PROFESSIONAL, LessonCategory.CONVERSATION,
            "Good stories have a clear beginning, middle and end. These phrases help you guide your listener.",
            listOf(
                PracticeItem("It all started when I missed my train.", null, "it ALL STAR-ted when I MISSED my TRAIN."),
                PracticeItem("To cut a long story short, we got there in the end.", null, "to CUT a LONG STOR-y SHORT, we GOT there in the END."),
                PracticeItem("Looking back, it was quite funny.", null, "LOOK-ing BACK, it was QUITE FUN-ny."),
            ),
            listOf("'Quite' in British English often means 'fairly', so 'quite funny' is a little softer than 'very funny'."),
            topic = "A memorable day",
            opening = "I'd love to hear a story. Can you tell me about a day you'll always remember?",
        )
        add(
            "s4-technical", "Explaining technical ideas", Stage.PROFESSIONAL, LessonCategory.PROFESSIONAL,
            "Explaining something technical clearly is a valuable skill. Short sentences and signposting phrases make it much easier to follow.",
            listOf(
                PracticeItem("Let me explain how it works.", null, "LET me ex-PLAIN HOW it WORKS."),
                PracticeItem("In simple terms, the system checks each request.", null, "in SIM-ple TERMS, the SYS-tem CHECKS each re-QUEST."),
                PracticeItem("The main advantage is that it's much faster.", null, "the MAIN ad-VAN-tage is that it's MUCH FAS-ter."),
                PracticeItem("Does that make sense so far?", null, "does that MAKE SENSE so FAR?"),
            ),
            listOf("'Advantage' in British English is usually /ədˈvɑːntɪdʒ/, with a long /ɑː/."),
            topic = "Explaining your work or a technology you know well",
            opening = "Imagine I'm a new colleague with no technical background. Could you explain what you work on?",
        )
        add(
            "s4-meetings", "Meetings", Stage.PROFESSIONAL, LessonCategory.PROFESSIONAL,
            "Meetings need polite ways to join in, agree, disagree and move things along.",
            listOf(
                PracticeItem("Could I just add something here?", null, "could I JUST ADD SOME-thing HERE?"),
                PracticeItem("Shall we move on to the next point?", null, "shall we MOVE ON to the NEXT POINT?"),
                PracticeItem("I take your point, but I'm a bit concerned about the timing.", null, "I TAKE your POINT, but I'm a BIT con-CERNED about the TIM-ing."),
                PracticeItem("Let's come back to that later.", null, "LET'S come BACK to that LA-ter."),
            ),
            listOf("British speakers often soften disagreement with 'a bit', 'slightly' or 'I wonder if...'."),
            topic = "A team meeting about a project deadline",
            opening = "Thanks for joining. We're a little behind on the project. What do you think is causing the delay?",
        )
        add(
            "s4-interview", "Job interviews", Stage.PROFESSIONAL, LessonCategory.PROFESSIONAL,
            "Let's prepare for an interview. Clear, confident answers with an example work best.",
            listOf(
                PracticeItem("One of my main strengths is attention to detail.", null, "ONE of my MAIN STRENGTHS is at-TEN-tion to DE-tail."),
                PracticeItem("For example, in my last role I led a small team.", null, "for ex-AM-ple, in my LAST ROLE I LED a SMALL TEAM."),
                PracticeItem("I'm keen to develop my leadership skills.", null, "I'm KEEN to de-VEL-op my LEAD-er-ship SKILLS."),
            ),
            listOf("'Keen to' is a common British way of saying 'eager to'."),
            topic = "A job interview",
            opening = "Thanks for coming in today. To start with, could you tell me a little about yourself?",
        )
        add(
            "s4-presentations", "Presentations", Stage.PROFESSIONAL, LessonCategory.PROFESSIONAL,
            "Signposting phrases tell your audience where you are in a talk. Pause briefly after each one.",
            listOf(
                PracticeItem("Today I'd like to talk about our results.", null, "to-DAY I'd LIKE to TALK about our re-SULTS."),
                PracticeItem("Moving on to my next point.", null, "MOV-ing ON to my NEXT POINT."),
                PracticeItem("To sum up, we've had a strong year.", null, "to SUM UP, we've HAD a STRONG YEAR."),
                PracticeItem("I'm happy to take any questions.", null, "I'm HAP-py to TAKE any QUES-tions."),
            ),
            topic = "Presenting a short update to your team",
            opening = "Whenever you're ready, give me a short update on something you're working on, as if I'm your audience.",
        )

        // ---------------- Stage 5: Advanced communication ----------------
        add(
            "s5-connected-speech", "Connected speech", Stage.ADVANCED, LessonCategory.CONNECTED_SPEECH,
            "In natural speech, words join together. Recognising this helps you understand fast speech and sound more relaxed.",
            listOf(
                PracticeItem("an apple", "/ən ˈæpl/", "a-NAPple (the n links across)"),
                PracticeItem("Not at all.", "/ˌnɒt ət ˈɔːl/", "not a-TALL"),
                PracticeItem("far away", "/ˌfɑːr əˈweɪ/", "fa-ra-WAY (linking r)"),
                PracticeItem("Did you eat?", "/ˈdɪdʒu ˈiːt/", "DID-joo EAT?"),
                PracticeItem("I'd rather not.", "/aɪd ˈrɑːðə ˈnɒt/", "I'd RA-ther NOT."),
            ),
            listOf(
                "Linking r: British speakers pronounce a final 'r' when the next word starts with a vowel, as in 'far away'.",
                "'Did you' often blends into /ˈdɪdʒu/ in relaxed speech.",
                "Many British speakers use a glottal stop for 't' in 'not at all'. Both versions are fine.",
            ),
        )
        add(
            "s5-intonation-attitude", "Intonation and attitude", Stage.ADVANCED, LessonCategory.INTONATION,
            "The same words can sound interested, doubtful or surprised depending on the melody of your voice.",
            listOf(
                PracticeItem("Really?", null, "REAL-ly? (rising: surprised and interested)"),
                PracticeItem("That's interesting.", null, "that's IN-ter-est-ing. (fall-rise: polite doubt)"),
                PracticeItem("Well, it depends.", null, "WELL, it de-PENDS. (fall-rise on 'well': hesitation)"),
                PracticeItem("That's brilliant!", null, "that's BRIL-liant! (high fall: genuine enthusiasm)"),
            ),
            listOf("A fall-rise is very common in British English for polite disagreement or reservation."),
        )
        add(
            "s5-opinions", "Expressing nuanced opinions", Stage.ADVANCED, LessonCategory.CONVERSATION,
            "Advanced speakers rarely say 'yes' or 'no' outright. They balance ideas and show they've listened.",
            listOf(
                PracticeItem("From my point of view, it's a positive change.", null, "from MY POINT of VIEW, it's a PO-si-tive CHANGE."),
                PracticeItem("I see where you're coming from, but I'm not sure I agree.", null, "I SEE where you're COM-ing FROM, but I'm NOT SURE I a-GREE."),
                PracticeItem("On balance, I'd say the benefits outweigh the costs.", null, "on BAL-ance, I'd SAY the BEN-e-fits out-WEIGH the COSTS."),
            ),
            topic = "Working from home versus working in an office",
            opening = "Some people love working from home and others really miss the office. Where do you stand on that?",
        )
        add(
            "s5-debate", "Debates and discussions", Stage.ADVANCED, LessonCategory.CONVERSATION,
            "In a debate you need to challenge ideas politely and support your view with reasons.",
            listOf(
                PracticeItem("That's a fair point; however, there's another side to it.", null, "that's a FAIR POINT; how-EV-er, there's a-NO-ther SIDE to it."),
                PracticeItem("I'm not entirely convinced.", null, "I'm NOT en-TIRE-ly con-VINCED."),
                PracticeItem("What evidence is there for that?", null, "WHAT EV-i-dence is THERE for THAT?"),
            ),
            topic = "Should city centres ban private cars?",
            opening = "Here's our debate topic: should city centres ban private cars? Tell me your view and your main reason.",
        )
        add(
            "s5-idioms", "Idioms and natural phrasing", Stage.ADVANCED, LessonCategory.EXPRESSIONS,
            "Idioms make your English sound natural, but they're best used sparingly and in the right situation.",
            listOf(
                PracticeItem("It's not really my cup of tea.", null, "it's NOT REAL-ly my CUP of TEA."),
                PracticeItem("I'm feeling a bit under the weather.", null, "I'm FEEL-ing a BIT UN-der the WEA-ther."),
                PracticeItem("Shall we call it a day?", null, "shall we CALL it a DAY?"),
                PracticeItem("It's a piece of cake.", null, "it's a PIECE of CAKE."),
            ),
            listOf(
                "'Not my cup of tea' politely means you don't enjoy something.",
                "'Under the weather' means slightly ill.",
                "'It's raining cats and dogs' is well known but sounds old-fashioned to many British people.",
            ),
        )
        add(
            "s5-spontaneous", "Spontaneous speaking", Stage.ADVANCED, LessonCategory.CONVERSATION,
            "Now let's practise thinking on your feet. There's no right answer. Take a breath, then keep talking for as long as you can.",
            listOf(
                PracticeItem("That's a tricky question; let me think.", null, "that's a TRICK-y QUES-tion; LET me THINK."),
                PracticeItem("I've never really thought about it before.", null, "I've NE-ver REAL-ly THOUGHT about it be-FORE."),
                PracticeItem("If I had to choose, I'd probably say...", null, "if I HAD to CHOOSE, I'd PROB-ab-ly SAY..."),
            ),
            listOf("Fillers like 'let me think' buy you time far more naturally than silence or 'umm'."),
            topic = "Imaginative questions",
            opening = "If you could live anywhere in the world for one year, where would you choose, and why?",
        )
        return list
    }
}
