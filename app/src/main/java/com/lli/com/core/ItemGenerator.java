package com.lli.com.core;

import com.lli.com.GameApp;

import java.util.Locale;

public class ItemGenerator {
    public static final String[] WEAPON_NAMES = {"مسدس", "بندقية", "رشاش", "سيف", "خنجر", "رمح", "قوس", "فأس", "قناصة", "صولجان", "نصل", "حربة", "مطرقة", "عصا", "خنجر غادر", "نصل قاطع"};
    public static final String[] ARMOR_NAMES = {"درع", "خوذة", "قفازات", "حذاء", "وشاح", "ترس", "تاج", "سوار سحري", "خف سريع", "درع حصين", "درع جلدي", "درع حديدي", "درع ذهبي", "درع أسطوري", "خوذة محارب", "خوذة ملكية", "قفازات جلدية", "قفازات حديدية", "حذاء خفيف", "حذاء ثقيل"};
    public static final String[] ACCESSORY_NAMES = {"خاتم", "قلادة", "قرط", "خلخال", "سوار نادب", "تعويذة", "ميدالية", "خرزة طاقة", "وشاح سحري", "حزام قوة", "تميمة حظ", "درع صغير"};
    public static final String[] SUFFIXES = {"التنين", "الظلام", "النور", "الأساطير", "الخلود", "الدمار", "العواصف", "البرق", "البركان", "السماء", "الأرواح", "الفوضى", "العدم", "النجوم", "الأرض", "البحار", "الغابة", "الصحراء", "الجليد", "النار"};
    public static final String[] ATTRIBUTES = {"الأسطوري", "الملعون", "العتيق", "المتوهج", "الصامت", "المرعب", "الذهبي", "الكريستالي", "الغامض", "الجبار", "المنسي", "المحطم", "المسحور", "اللامع", "الخفي", "المدمر", "الفضي", "الزمردي", "الياقوتي", "الماسي"};

    public static final String[] WEAPON_NAMES_EN = {"Pistol", "Rifle", "Machine Gun", "Sword", "Dagger", "Spear", "Bow", "Axe", "Sniper", "Mace", "Blade", "Bayonet", "Hammer", "Staff", "Treacherous Dagger", "Cutting Blade"};
    public static final String[] ARMOR_NAMES_EN = {"Shield", "Helmet", "Gloves", "Boots", "Scarf", "Buckler", "Crown", "Magic Bracelet", "Swift Shoes", "Impregnable Shield", "Leather Shield", "Iron Shield", "Golden Shield", "Legendary Shield", "Warrior Helmet", "Royal Helmet", "Leather Gloves", "Iron Gloves", "Light Boots", "Heavy Boots"};
    public static final String[] ACCESSORY_NAMES_EN = {"Ring", "Necklace", "Earring", "Anklet", "Tattooed Bracelet", "Charm", "Medal", "Energy Bead", "Magic Scarf", "Power Belt", "Lucky Talisman", "Small Shield"};
    public static final String[] SUFFIXES_EN = {"of the Dragon", "of Darkness", "of Light", "of Legends", "of Immortality", "of Destruction", "of Storms", "of Lightning", "of the Volcano", "of the Sky", "of Spirits", "of Chaos", "of Nothingness", "of the Stars", "of the Earth", "of the Seas", "of the Forest", "of the Desert", "of Ice", "of Fire"};
    public static final String[] ATTRIBUTES_EN = {"Legendary", "Cursed", "Ancient", "Glowing", "Silent", "Terrifying", "Golden", "Crystal", "Mysterious", "Mighty", "Forgotten", "Shattered", "Enchanted", "Shiny", "Hidden", "Devastating", "Silver", "Emerald", "Ruby", "Diamond"};

    public static class Rarity {
        public String name;
        public int minLvl;
        public int maxLvl;
        public int[] boostRange;
        public Rarity(String name, int minLvl, int maxLvl, int boostMin, int boostMax) {
            this.name = name;
            this.minLvl = minLvl;
            this.maxLvl = maxLvl;
            this.boostRange = new int[]{boostMin, boostMax};
        }
    }

    public static final Rarity[] RARITY_TYPES = {
            new Rarity("عادي", 1, 20, 5, 20),
            new Rarity("غير عادي", 21, 40, 21, 50),
            new Rarity("نادر", 41, 60, 51, 120),
            new Rarity("ملحمي", 61, 80, 300, 800),
            new Rarity("أسطوري", 81, 100, 1000, 2500),
            new Rarity("خرافي", 101, 9999, 5000, 15000)
    };

    public static Rarity getRarityByIndex(int forcedIdx) {
        int idx = Math.max(0, Math.min(RARITY_TYPES.length - 1, forcedIdx - 1));
        return RARITY_TYPES[idx];
    }

    public static Rarity getRarityForLevel(int pLvl) {
        for (Rarity r : RARITY_TYPES) {
            if (pLvl >= r.minLvl && pLvl <= r.maxLvl) return r;
        }
        return RARITY_TYPES[0];
    }

    public static ItemData generateRandomItem(int pLvl, int forcedRarityIdx) {
        boolean en = GameApp.isEn;
        Rarity rarity = forcedRarityIdx > 0 ? getRarityByIndex(forcedRarityIdx) : getRarityForLevel(pLvl);

        // 10% scroll chance
        if (NumberUtil.rand(1, 10) == 1) {
            int reqLvl = Math.max(1, pLvl + NumberUtil.rand(-5, 5));
            double rarityMult = 1;
            if (rarity.name.equals("غير عادي")) rarityMult = 1.5;
            else if (rarity.name.equals("نادر")) rarityMult = 2.5;
            else if (rarity.name.equals("ملحمي")) rarityMult = 5;
            else if (rarity.name.equals("أسطوري")) rarityMult = 12;
            else if (rarity.name.equals("خرافي")) rarityMult = 30;
            int scrollPower = (int) Math.floor(reqLvl * rarityMult * NumberUtil.rand(5, 10));
            int sHp = scrollPower;
            int sStr = (int) Math.floor(scrollPower / 10.0);
            int sLck = (int) Math.floor(rarityMult * 2);

            String[] scrollPrefixes = {"مخطوطة", "لفافة", "رسالة", "كتاب", "بردية"};
            String[] scrollTypes = {"القوة", "الحماية", "الحكمة", "البركة", "الأساطير", "التنانين", "الخالدين"};
            String[] scrollPrefixesEn = {"Scroll", "Parchment", "Letter", "Book", "Papyrus"};
            String[] scrollTypesEn = {"of Power", "of Protection", "of Wisdom", "of Blessing", "of Legends", "of Dragons", "of the Immortals"};
            String name;
            if (en) {
                name = scrollPrefixesEn[NumberUtil.rand(0, scrollPrefixesEn.length - 1)] + " " + scrollTypesEn[NumberUtil.rand(0, scrollTypesEn.length - 1)] + " " + SUFFIXES_EN[NumberUtil.rand(0, SUFFIXES_EN.length - 1)] + " " + ATTRIBUTES_EN[NumberUtil.rand(0, ATTRIBUTES_EN.length - 1)];
            } else {
                name = scrollPrefixes[NumberUtil.rand(0, scrollPrefixes.length - 1)] + " " + scrollTypes[NumberUtil.rand(0, scrollTypes.length - 1)] + " " + SUFFIXES[NumberUtil.rand(0, SUFFIXES.length - 1)] + " " + ATTRIBUTES[NumberUtil.rand(0, ATTRIBUTES.length - 1)];
            }

            ItemData it = new ItemData();
            it.name = name;
            it.rarity = rarity.name;
            it.category = "أدوات مساعدة";
            it.subType = "مخطوطات";
            it.level = reqLvl;
            it.isScroll = true;
            it.desc = en
                    ? String.format(Locale.US, "An ancient scroll [%s] that increases power when read.\nGrants: +%d max health, +%d attack, +%d luck.\nRequired level: %d", GameApp.rarity(rarity.name), sHp, sStr, sLck, reqLvl)
                    : String.format(Locale.US, "مخطوطة قديمة [%s] تزيد القوة عند قراءتها.\nتمنح: +%d صحة قصوى، +%d هجوم، +%d حظ.\nالمستوى المطلوب: %d", rarity.name, sHp, sStr, sLck, reqLvl);
            it.stats = new Stats();
            it.stats.maxHp = sHp;
            it.stats.strength = sStr;
            it.stats.luck = sLck;
            it.boosts.hp = sHp;
            it.boosts.str = sStr;
            it.boosts.lck = sLck;
            it.price = scrollPower * 20L;
            return it;
        }

        int reqLvl = Math.max(1, pLvl + NumberUtil.rand(-5, 5));
        int basePower = (int) Math.floor(reqLvl * NumberUtil.rand(1, 2) * 0.5);
        int boostVal = basePower + NumberUtil.rand(rarity.boostRange[0], rarity.boostRange[1]);

        String itemCategory = "سلاح";
        String itemSubType = "";
        String namePrefix = "";
        Boosts boosts = new Boosts();

        int randCat = NumberUtil.rand(1, 3);
        if (randCat == 1) {
            itemCategory = "سلاح";
            int wi = NumberUtil.rand(0, WEAPON_NAMES.length - 1);
            namePrefix = en ? WEAPON_NAMES_EN[wi] : WEAPON_NAMES[wi];
            itemSubType = weaponSubType(namePrefix);
            boosts.str = boostVal;
        } else if (randCat == 2) {
            itemCategory = "دروع";
            int mi = NumberUtil.rand(0, ARMOR_NAMES.length - 1);
            namePrefix = en ? ARMOR_NAMES_EN[mi] : ARMOR_NAMES[mi];
            itemSubType = armorSubType(namePrefix);
            boosts.end = boostVal;
        } else {
            itemCategory = "أدوات مساعدة";
            int ai2 = NumberUtil.rand(0, ACCESSORY_NAMES.length - 1);
            namePrefix = en ? ACCESSORY_NAMES_EN[ai2] : ACCESSORY_NAMES[ai2];
            if (namePrefix.contains("خاتم") || namePrefix.contains("Ring")) itemSubType = GameApp.T("خواتم", "Rings");
            else if (namePrefix.contains("قلادة") || namePrefix.contains("Necklace")) itemSubType = GameApp.T("قلادات", "Necklaces");
            else itemSubType = GameApp.T("إكسسوارات", "Accessories");
            if (NumberUtil.rand(1, 2) == 1) {
                boosts.end = boostVal;
            } else {
                boosts.hp = (int) Math.floor(boostVal * 0.5);
            }
        }

        int si = NumberUtil.rand(0, SUFFIXES.length - 1);
        int ai = NumberUtil.rand(0, ATTRIBUTES.length - 1);
        String s = en ? SUFFIXES_EN[si] : SUFFIXES[si];
        String a = en ? ATTRIBUTES_EN[ai] : ATTRIBUTES[ai];
        String name = en ? (a + " " + namePrefix + " " + s) : (namePrefix + " " + s + " " + a);
        String itemRarity = rarity.name;

        String desc = en
                ? "Item [" + GameApp.rarity(itemRarity) + "] of type [" + itemSubType + "] increases"
                : "أداة [" + itemRarity + "] من نوع [" + itemSubType + "] تزيد";
        int hpMultiplier = 1;
        if (rarity.name.equals("خرافي")) hpMultiplier = 4;
        else if (rarity.name.equals("أسطوري")) hpMultiplier = 3;
        else if (rarity.name.equals("ملحمي")) hpMultiplier = 2;

        if (boosts.str > 0) desc = desc + (en ? " attack by " : " الهجوم بـ ") + (long) boosts.str;
        else if (boosts.end > 0) desc = desc + (en ? " defense by " : " الدفاع بـ ") + (long) boosts.end;
        else if (boosts.hp > 0 && itemCategory.equals("أدوات مساعدة")) desc = desc + (en ? " health by " : " الصحة بـ ") + (long) boosts.hp;

        int randType = NumberUtil.rand(1, 3);
        if (randType == 1) {
            boosts.hp = boostVal * hpMultiplier;
            desc = desc + (en ? " and health by " : " والصحة بـ ") + (long) (boostVal * hpMultiplier);
        } else if (randType == 2) {
            boosts.agi = (int) Math.floor(boostVal / 5.0);
            desc = desc + (en ? " and agility by " : " والرشاقة بـ ") + (int) Math.floor(boostVal / 5.0);
        } else {
            boosts.lck = (int) Math.floor(boostVal / 5.0);
            desc = desc + (en ? " and luck by " : " والحظ بـ ") + (int) Math.floor(boostVal / 5.0);
        }
        desc = desc + (en ? "\nRequired level: " : "\nالمستوى المطلوب: ") + reqLvl;

        ItemData it = new ItemData();
        it.name = name;
        it.rarity = itemRarity;
        it.category = itemCategory;
        it.subType = itemSubType;
        it.level = reqLvl;
        it.desc = desc;
        it.boosts = boosts;
        it.price = boostVal * 10L;
        return it;
    }

    private static String weaponSubType(String p) {
        if (p.contains("سيف") || p.contains("نصل") || p.contains("Sword") || p.contains("Blade")) return GameApp.T("سيوف", "Swords");
        if (p.contains("فأس") || p.contains("Axe")) return GameApp.T("فؤوس", "Axes");
        if (p.contains("مطرقة") || p.contains("Hammer")) return GameApp.T("مطارق", "Hammers");
        if (p.contains("قوس") || p.contains("Bow")) return GameApp.T("أقواس", "Bows");
        if (p.contains("خنجر") || p.contains("سكين") || p.contains("Dagger")) return GameApp.T("خناجر", "Daggers");
        if (p.contains("رمح") || p.contains("حربة") || p.contains("Spear") || p.contains("Bayonet")) return GameApp.T("رماح", "Spears");
        if (p.contains("مسدس") || p.contains("Pistol")) return GameApp.T("مسدسات", "Pistols");
        if (p.contains("بندقية") || p.contains("Rifle")) return GameApp.T("بنادق", "Rifles");
        if (p.contains("رشاش") || p.contains("Machine Gun")) return GameApp.T("رشاشات", "Machine Guns");
        if (p.contains("قناصة") || p.contains("Sniper")) return GameApp.T("قناصات", "Snipers");
        if (p.contains("صولجان") || p.contains("Mace") || p.contains("عصا") || p.contains("Staff")) return GameApp.T("صولجانات", "Maces");
        return GameApp.T("أدوات قتالية", "Combat Tools");
    }

    private static String armorSubType(String p) {
        if (p.contains("درع") || p.contains("Shield") || p.contains("Buckler")) return GameApp.T("دروع ثقيلة", "Heavy Shields");
        if (p.contains("خوذة") || p.contains("تاج") || p.contains("Helmet") || p.contains("Crown")) return GameApp.T("خوذ", "Helmets");
        if (p.contains("حذاء") || p.contains("خف") || p.contains("Boots") || p.contains("Shoes")) return GameApp.T("أحذية", "Boots");
        if (p.contains("قفازات") || p.contains("سوار") || p.contains("Gloves") || p.contains("Bracelet")) return GameApp.T("قفازات", "Gloves");
        return GameApp.T("دروع خفيفة", "Light Shields");
    }
}
