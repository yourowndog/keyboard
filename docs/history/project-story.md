# Project OmniBoard: The Bespoke Engine
## "The Keyboard That Shouldn't Exist"

### The Short Version (For Mom)
You know how everyone hates their phone keyboard? It’s either too dumb, too small, or it gets in the way. I spent the last month fixing that by building my own from scratch.

It looks like a standard keyboard, but under the hood, it’s a custom-built engine designed for exactly one person: **Me**. It connects directly to the most powerful AI in the world (Whisper) so I can talk normally and have it type perfect text instantly. It has special keys that let me control my computer remotely, which usually isn't possible on a phone.

I can’t buy this app. It doesn't exist in the store. So I had to build it.

---

### The Full Story (For Dad)

#### Act I: The "Goldilocks" Paradox
The Galaxy S25 Ultra is a supercomputer in my pocket. It has more processing power than the laptops we used ten years ago. But the interface—the keyboard—is a bottleneck. It forces me to interact with this supercomputer like a toddler playing with blocks.

I run a full Linux environment (Termux) on my phone. I write code, manage servers, and run scripts. But the input tools were broken:
*   **Gboard (Google):** Great prediction, but useless for code. No `Ctrl` key, no `Esc`, and its voice typing is a "black box" that fails on technical terms.
*   **Hacker's Keyboard:** Has the keys, but the buttons are microscopic (pixel-width). It’s unusable for actual typing.

I needed the "Goldilocks" solution: The ergonomic comfort of a modern smart keyboard combined with the raw utility of a Linux terminal. And I needed it to be smarter than Google.

#### Act II: The Invisible Wiring
A keyboard isn't just an app. In Android, it’s an **Input Method Editor (IME)**. It sits deep in the operating system stack, acting as a gatekeeper between the user and every other app.

To get the functionality I needed, I had to bypass the standard Android text input connection.
*   **The Impossible Keys:** Android doesn't really *want* a software keyboard to have an `Escape` key or a `Control` key. Those are hardware concepts. To make them work, I had to write a low-level handler in Kotlin (`KeyboardManager.kt`) that injects **raw key events** directly into the system event queue.
*   **The Result:** When I press `Ctrl` + `C` on my phone, it doesn't just type a letter. It sends a signal interrupt to the Linux shell, killing a runaway process. This is practically unheard of in a touch-screen keyboard.

#### Act III: The Data Janitor
The "Smart" in a smart keyboard comes from its dictionary. But standard dictionaries are garbage. They are lists of words, not maps of meaning.
*   **The "Frankenstein" Dataset:** I had to merge two massive datasets. One was from **TV Subtitles** (natural, conversational English). The other was from **Academic Papers** (sterile, formal English).
*   **The Conflict:** Suddenly, the keyboard thought I was writing a thesis every time I texted you. Words like "variable" and "render" had higher probability weights than "love" or "dinner."
*   **The "De-Pornification":** Bigram data (lists of which words follow other words) is filthy. The internet is 90% porn. I had to write Python scripts to scrub thousands of "sexy [noun]" predictions so the keyboard wouldn't embarrass me in a professional setting.
*   **The "Don't" Problem:** I realized too late that my clean dataset stripped out contractions. It didn't know "don't" or "can't"—it only knew "do not" and "cannot." I had to build a custom logic layer (`SymSpellManager.kt`) that prioritizes contractions, effectively teaching the AI how to speak casually.

#### Act IV: The Voice of God (Whisper Integration)
This is the killer feature.
*   **Standard Voice Typing:** You speak -> Phone guesses -> Types "Hello world." (Often wrong, slow, offline).
*   **OmniBoard Voice:** You speak -> I record the raw audio -> Securely pipe it to OpenAI's **Whisper** model (the same brain behind ChatGPT) -> It transcribes with near-human accuracy -> I inject the text.

It handles accents, technical jargon, and mumbling perfectly. It doesn't just "dictate"; it understands.

#### Act V: Coding in the Dark
The hardest part? I built this **on the phone itself**.
I didn't use a desktop with helpful tools (IDEs) that underline my mistakes in red. I wrote the code in a terminal, on the phone, often "blind." It was like remodeling a house while living inside it, with the lights off.

### The Result
It’s finished. It matches my "L-CARS" (Star Trek) visual theme. It respects my privacy. It speaks my language. And it grants me full control over my digital environment.

It is the ultimate "Power User" tool, and it is the only one of its kind.
