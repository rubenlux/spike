package spike.peerdirectory.domain

import java.security.MessageDigest

object SasGenerator {
    private const val DOMAIN_TAG = "OFFLINE_PROTOCOL_SAS_V1"

    val WORD_LIST: List<String> = listOf(
        "ALARM", "ALASKA", "ALBERT", "ALBINO", "ALBUM", "ALCOVE", "ALERT", "ALIBI",
        "ALICE", "ALIEN", "ALMANAC", "ALMOND", "ALPACA", "ALPHA", "ALPINE", "AMBER",
        "AMIGO", "AMMONIA", "ANCHOR", "ANCIENT", "ANGEL", "ANGORA", "ANIMAL", "ANIME",
        "ANNEX", "ANSWER", "ANTENA", "ANTIQUE", "APEX", "APOLLO", "APRON", "AQUA",
        "ARCADIA", "ARCH", "ARCTIC", "ARENA", "ARGON", "ARMADA", "ARNOLD", "AROMA",
        "ARROW", "ARTHUR", "ARTIST", "ASPECT", "ASPEN", "ASSIST", "ASTER", "ATLAS",
        "ATOM", "ATRIUM", "ATTIC", "AUDIO", "AUGUST", "AURA", "AURORA", "AVENUE",
        "AVIATOR", "AVOCADO", "AWARD", "AXIS", "AZTEC", "AZURE", "BACON", "BADGE",
        "BALLET", "BAMBOO", "BANJO", "BARON", "BARREL", "BASALT", "BEACON", "BEETLE",
        "BELL", "BERRY", "BICYCLE", "BINGO", "BISON", "BLANKET", "BLAZER", "BLIZZARD",
        "BONUS", "BORDER", "BOTANY", "BOULDER", "BOUNTY", "BOXER", "BRAVO", "BREEZE",
        "BRONZE", "CACTUS", "CADET", "CAMEL", "CAMPUS", "CANARY", "CANDLE", "CANYON",
        "CAPTAIN", "CARAVAN", "CARBON", "CASTLE", "CAVALIER", "CEDAR", "CELLO", "CEMENT",
        "CENTRAL", "CEREAL", "CHAIR", "CHANCE", "CHANNEL", "CHARLIE", "CHEF", "CHERRY",
        "CHESS", "CHROME", "CIRCUIT", "CITRUS", "CLOVER", "COBALT", "COMET", "COMPASS",
        "COPPER", "CORAL", "COSMOS", "COTTON", "CRATER", "CRISTAL", "CRUISE", "CRYSTAL",
        "DELTA", "DESERT", "DIAMOND", "DINOSAUR", "DOLPHIN", "DOMINO", "DRAGON", "ECHO",
        "ECLIPSE", "EMERALD", "ENERGY", "ENGINE", "ENIGMA", "FALCON", "FESTIVAL", "FILTER",
        "FLAME", "FLASH", "FOREST", "FOXTROT", "GALAXY", "GARDEN", "GARLIC", "GEMINI",
        "GHOST", "GIRAFFE", "GLACIER", "GLOBE", "GOLF", "GRAVITY", "GUITAR", "HARBOR",
        "HARMONY", "HAWK", "HELMET", "HERO", "HORIZON", "HOTEL", "HUNTER", "ICEBERG",
        "IGLOO", "IMAGE", "IMPACT", "IMPULSE", "INDEX", "INDIA", "INDIGO", "ISLAND",
        "IVORY", "JACKET", "JAGUAR", "JASMINE", "JAZZ", "JEWEL", "JOKER", "JULIET",
        "JUNGLE", "JUPITER", "KARATE", "KILO", "KINETIC", "KING", "KIWI", "LAGOON",
        "LANTERN", "LASER", "LEMON", "LEOPARD", "LIBERTY", "LIGHT", "LIMA", "LION",
        "LIZARD", "LOTUS", "LUNAR", "MAGNET", "MANGO", "MARBLE", "MATRIX", "MELON",
        "METEOR", "MIKE", "MIRROR", "MONSTER", "MOON", "MOSAIC", "MUSEUM", "NEBULA",
        "NEPTUNE", "NICKEL", "NINJA", "NITRO", "NOMAD", "NORTH", "NOVEMBER", "NUCLEUS",
        "OASIS", "OCTOPUS", "OLIVE", "OLYMPIC", "OMEGA", "OPAL", "ORBIT", "ORCHID",
        "ORIGAMI", "ORION", "OSCAR", "OXYGEN", "PALM", "PANDA", "PANTHER", "PAPA",
        "PARADOX", "PARROT", "PASSION", "PELICAN", "PENGUIN", "PHOENIX", "PIANO", "PILOT",
        "PIRATE", "PLANET", "PLASMA", "PLATINUM", "PLUTO", "POLAR", "PONY", "PRISM",
        "PROTON", "PULSE", "PYTHON", "QUARTZ", "QUEBEC", "RADAR", "RADIUS", "RAINBOW",
        "RAPTOR", "RAVEN", "RAYON", "REACTOR", "RECORD", "REFLEX", "REGAL", "REMOTE",
        "RIVER", "ROBOT", "ROCKET", "ROMEO", "RUBY", "SAFARI", "SALMON", "SAMURAI",
        "SAPPHIRE", "SATURN", "SCOUT", "SHADOW", "SIGNAL", "SILVER", "SIERRA", "SKYLARK",
        "SOLAR", "SONAR", "SPARK", "SPHERE", "SPIRIT", "SQUIRREL", "STADIUM", "STAR",
        "STORM", "SUMMIT", "SUNSET", "SYMBOL", "TANGO", "TEMPO", "TENNIS", "TIGER",
        "TITAN", "TOPAZ", "TORNADO", "TORPEDO", "TOWER", "TROPIC", "TULIP", "TURTLE",
        "ULTRA", "UNIFORM", "URANIUM", "VALLEY", "VELVET", "VICTOR", "VIKING", "VORTEX",
        "VULCAN", "WALNUT", "WHISKEY", "WINTER", "WIZARD", "XRAY", "YANKEE", "ZULU"
    )

    fun generateSas(
        sessionBindingHash: String,
        secureSessionTranscriptHash: String? = null
    ): ShortAuthenticationString {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN_TAG.toByteArray(Charsets.UTF_8))
        digest.update(sessionBindingHash.toByteArray(Charsets.UTF_8))
        secureSessionTranscriptHash?.let {
            digest.update(it.toByteArray(Charsets.UTF_8))
        }
        val hash = digest.digest()

        val numRaw = ((hash[0].toLong() and 0xFF) shl 24) or
                ((hash[1].toLong() and 0xFF) shl 16) or
                ((hash[2].toLong() and 0xFF) shl 8) or
                (hash[3].toLong() and 0xFF)

        val numericCode = String.format("%06d", Math.abs(numRaw % 1_000_000L))

        val idx1 = hash[4].toInt() and 0xFF
        val idx2 = hash[5].toInt() and 0xFF
        val idx3 = hash[6].toInt() and 0xFF

        val words = listOf(
            WORD_LIST[idx1 % WORD_LIST.size],
            WORD_LIST[idx2 % WORD_LIST.size],
            WORD_LIST[idx3 % WORD_LIST.size]
        )

        return ShortAuthenticationString(
            numericCode = numericCode,
            words = words
        )
    }
}
