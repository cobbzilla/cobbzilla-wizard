package org.cobbzilla.wizard.util;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.Random;

import static java.lang.Math.abs;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.safeFunctionName;

public class TestNames {

    public static final String[] COLORS = {
        "Aliceblue", "Antiquewhite", "Aqua", "Aquamarine", "Azure", "Beige", "Bisque", "Black", "Blanchedalmond", "Blue",
        "Blueviolet", "Brown", "Burlywood", "Cadetblue", "Chartreuse", "Chocolate", "Coral", "Cornflowerblue", "Cornsilk",
        "Crimson", "Cyan", "Darkblue", "Darkcyan", "Darkgoldenrod", "Darkgray", "Darkgreen", "Darkkhaki", "Darkmagenta",
        "Darkolivegreen", "Darkorange", "Darkorchid", "Darkred", "Darksalmon", "Darkseagreen", "Darkslateblue", "Darkslategray",
        "Darkturquoise", "Darkviolet", "Deeppink", "Deepskyblue", "Dimgray", "Dodgerblue", "Firebrick", "Floralwhite",
        "Forestgreen", "Fuchsia", "Gainsboro", "Ghostwhite", "Gold", "Goldenrod", "Gray", "Green", "Greenyellow", "Honeydew",
        "Hotpink", "Indianred", "Indigo", "Ivory", "Khaki", "Lavender", "Lavenderblush", "Lawngreen", "Lemonchiffon", "Lightblue",
        "Lightcoral", "Lightcyan", "Lightgoldenrodyellow", "Lightgreen", "Lightgrey", "Lightpink", "Lightsalmon", "Lightseagreen",
        "Lightskyblue", "Lightslategray", "Lightsteelblue", "Lightyellow", "Lime", "Limegreen", "Linen", "Magenta", "Maroon",
        "Mediumauqamarine", "Mediumblue", "Mediumorchid", "Mediumpurple", "Mediumseagreen", "Mediumslateblue", "Mediumspringgreen",
        "Mediumturquoise", "Mediumvioletred", "Midnightblue", "Mintcream", "Mistyrose", "Moccasin", "Navajowhite", "Navy",
        "Oldlace", "Olive", "Olivedrab", "Orange", "Orangered", "Orchid", "Palegoldenrod", "Palegreen", "Paleturquoise",
        "Palevioletred", "Papayawhip", "Peachpuff", "Peru", "Pink", "Plum", "Powderblue", "Purple", "Red", "Rosybrown", "Royalblue",
        "Saddlebrown", "Salmon", "Sandybrown", "Seagreen", "Seashell", "Sienna", "Silver", "Skyblue", "Slateblue", "Slategray",
        "Snow", "Springgreen", "Steelblue", "Tan", "Teal", "Thistle", "Tomato", "Turquoise", "Violet", "Wheat", "White",
        "Whitesmoke", "Yellow", "YellowGreen"
    };

    public static final String[] FRUITS = {
        "Apple", "Apricot", "Bilberry", "Blackberry", "Blueberry", "Boysenberry", "Cantaloupe", "Cherry",
        "Coconut", "Cranberry", "Date", "Dragonfruit", "Elderberry", "Fig", "Gooseberry", "Grape",
        "Grapefruit", "Guava", "Huckleberry", "Lemon", "Lime", "Lychee", "Mango", "Melon", "Cantaloupe",
        "Honeydew", "Watermelon", "Mulberry", "Nectarine", "Olive", "Orange", "Clementine", "Tangerine",
        "Papaya", "Passionfruit", "Peach", "Pear", "Persimmon", "Plum", "Pineapple", "Pomegranate",
        "Pomelo", "Raspberry", "Strawberry", "Banana", "Avocado"
    };

    public static final String[] NATIONALITIES = {
        "Afghan", "Albanian", "Algerian", "Andorran", "Angolan", "Argentinian", "Armenian", "Australian",
        "Austrian", "Azerbaijani", "Bahamian", "Bahraini", "Bangladeshi", "Barbadian", "Belarusian", "Belgian",
        "Belizean", "Beninese", "Bhutanese", "Bolivian", "Bosnian", "Botswanan", "Brazilian", "British", "Bruneian",
        "Bulgarian", "Burkinese", "Burmese", "Burundian", "Cambodian", "Cameroonian", "Canadian", "Cape", "Verdean",
        "Chadian", "Chilean", "Chinese", "Colombian", "Congolese", "Costa", "Rican", "Croatian", "Cuban", "Cypriot",
        "Czech", "Danish", "Djiboutian", "Dominican", "Dominican", "Ecuadorean", "Egyptian", "Salvadorean", "English",
        "Eritrean", "Estonian", "Ethiopian", "Fijian", "Finnish", "French", "Gabonese", "Gambian", "Georgian", "German",
        "Ghanaian", "Greek", "Grenadian", "Guatemalan", "Guinean", "Guyanese", "Haitian", "Dutch", "Honduran", "Hungarian",
        "Icelandic", "Indian", "Indonesian", "Iranian", "Iraqi", "Irish", "Italian", "Jamaican", "Japanese", "Jordanian",
        "Kazakh", "Kenyan", "Kuwaiti", "Laotian", "Latvian", "Lebanese", "Liberian", "Libyan", "Lithuanian", "Macedonian",
        "Madagascan", "Malawian", "Malaysian", "Maldivian", "Malian", "Maltese", "Mauritanian", "Mauritian", "Mexican",
        "Moldovan", "Monacan", "Mongolian", "Montenegrin", "Moroccan", "Mozambican", "Namibian", "Nepalese", "Dutch",
        "Nicaraguan", "Nigerien", "Nigerian", "North", "Korean", "Norwegian", "Omani", "Pakistani", "Panamanian", "Guinean",
        "Paraguayan", "Peruvian", "Philippine", "Polish", "Portuguese", "Qatari", "Romanian", "Russian", "Rwandan", "Saudi",
        "Scottish", "Senegalese", "Serbian", "Seychellois", "Sierra", "Leonian", "Singaporean", "Slovak", "Slovenian",
        "Somali", "South", "African", "South", "Korean", "Spanish", "Sri", "Lankan", "Sudanese", "Surinamese", "Swazi",
        "Swedish", "Swiss", "Syrian", "Taiwanese", "Tadjik", "Tanzanian", "Thai", "Togolese", "Trinidadian<br>", "Tunisian",
        "Turkish", "Turkmen", "Tuvaluan", "Ugandan", "Ukrainian", "British", "American", "Uruguayan", "Uzbek", "Vanuatuan",
        "Venezuelan", "Vietnamese", "Welsh", "Western", "Samoan", "Yemeni", "Yugoslav", "Zaïrean", "Zambian", "Zimbabwean"
    };

    public static final String[] ANIMALS = {
        "Elephant", "Alligator", "Kestrel", "Condor", "Arctic_Fox", "Bald_Eagle", "Black_Swan", "Duck", "Burrowing_Owl",
        "Sea_Lion", "Chinchilla", "Collared_Peccary", "Rabbit", "Snake", "Hedgehog", "Owl", "Bear", "Leopard", "Shark",
        "Panda", "Lynx", "Llama", "Marine_Toad", "Mouflon", "Musk_Ox", "Arrow_Frog", "Porcupine", "Tortoise", "Red_Panda",
        "Lemur", "Hawk", "Rhea", "Reindeer", "Siberian_Tiger", "Snow_Leopard", "Snowy_Owl", "Whites_Tree_Frog", "Wild_Boar",
        "Invertebrate", "Centipede", "Crustacean", "Hermit_Crab", "Wood_Louse", "Bullet_Ant", "Carpenter_Bee",
        "Honey_Pot_Ant", "Honeybee", "Snail", "Spider", "Scorpion", "Fish", "Zebra", "Tiger", "Crocodile", "Lizard",
        "Snake", "King_Cobra", "Turtle", "Bird", "Penguin", "Bat", "Cheetah", "Mongoose", "Jaguar", "Kinkajou",
        "Lion", "Otter", "Polar_Bear", "Puma", "Red_Panda", "Sand_Cat", "Slender", "Hyena", "Rhinoceros", "Gazelle",
        "Goat", "Hippopotamus", "Okapi", "Pig", "Warthog", "Hyrax", "Lemur", "Monkey", "Ape", "Chimpanzee", "Gorilla",
        "Kangaroo", "Opossum", "Giraffe", "Killer_Whale", "Horse", "Wolf", "Mole", "Dingo", "Deer", "Emu", "Crocodile",
        "Bobcat", "Lion", "Squirrel"
    };

    private static final Random rand = new Random();

    public static String safeName () {
        return safeFunctionName(nationality())+"-"+safeFunctionName(fruit())+"-"+RandomStringUtils.randomAlphanumeric(10);
    }

    public static String name() { return nationality() + " " + fruit(); }

    public static String color() { return COLORS[rand.nextInt(COLORS.length)]; }
    public static String fruit() { return FRUITS[rand.nextInt(FRUITS.length)]; }
    public static String nationality() { return NATIONALITIES[rand.nextInt(NATIONALITIES.length)]; }
    public static String animal() { return ANIMALS[rand.nextInt(ANIMALS.length)]; }

    public static String color(String val) { return COLORS[abs(val.hashCode()) % COLORS.length]; }
    public static String fruit(String val) { return FRUITS[abs(val.hashCode()) % FRUITS.length]; }
    public static String nationality(String val) { return NATIONALITIES[abs(val.hashCode()) % NATIONALITIES.length]; }
    public static String animal(String val) { return ANIMALS[abs(val.hashCode()) % ANIMALS.length]; }

    public static String safeColor() { return color()+"-"+RandomStringUtils.randomAlphanumeric(10); }
    public static String safeAnimal() { return animal()+"-"+RandomStringUtils.randomAlphanumeric(10); }
    public static String safeFruit() { return fruit()+"-"+RandomStringUtils.randomAlphanumeric(10); }
    public static String safeNationality() { return nationality()+"-"+RandomStringUtils.randomAlphanumeric(10); }

    public static String safeColor(String val) { return color(val)+"-"+sha256_hex(val).substring(0, 8); }
    public static String safeAnimal(String val) { return animal(val)+"-"+sha256_hex(val).substring(0, 8); }
    public static String safeFruit(String val) { return fruit(val)+"-"+sha256_hex(val).substring(0, 8); }
    public static String safeNationality(String val) { return nationality(val)+"-"+sha256_hex(val).substring(0, 8); }

    public static String replaceTestNames(String val) {
        if (val != null) {
            while (val.contains("~color~")) val = val.replaceFirst("~color~", safeColor());
            while (val.contains("~animal~")) val = val.replaceFirst("~animal~", safeAnimal());
            while (val.contains("~fruit~")) val = val.replaceFirst("~fruit~", safeFruit());
            while (val.contains("~nationality~")) val = val.replaceFirst("~nationality~", safeNationality());
        }
        return val;
    }

}
