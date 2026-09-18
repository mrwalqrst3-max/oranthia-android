package com.lli.com.core;

import android.app.AlertDialog;

import com.lli.com.GameApp;
import com.lli.com.ui.U;

public class PrestigeSystem {
    public static final String[] PRESTIGE_RANKS = {
            GameApp.T("المحارب الناشئ", "Novice Warrior"), GameApp.T("المقاتل المتمرس", "Experienced Fighter"), GameApp.T("الفارس النبيل", "Noble Knight"), GameApp.T("المبارز البارع", "Master Swordsman"), GameApp.T("قائد الكتيبة", "Battalion Commander"),
            GameApp.T("الجنرال المهيب", "Majestic General"), GameApp.T("حارس العرش", "Guardian of the Throne"), GameApp.T("سيد السيوف", "Master of Swords"), GameApp.T("مدمر الحصون", "Fortress Destroyer"), GameApp.T("صائد التنانين", "Dragon Hunter"),
            GameApp.T("المحارب الأسطوري", "Legendary Warrior"), GameApp.T("البطل الخالد", "Immortal Hero"), GameApp.T("أمير الحرب", "Warlord"), GameApp.T("الدوق الملكي", "Royal Duke"), GameApp.T("الكونت المظلم", "Dark Count"),
            GameApp.T("البارون الذهبي", "Golden Baron"), GameApp.T("الماركيز العظيم", "Grand Marquis"), GameApp.T("نائب الملك", "Viceroy"), GameApp.T("الملك الشاب", "Young King"), GameApp.T("الملك العظيم", "Great King"),
            GameApp.T("السلطان المهيب", "Majestic Sultan"), GameApp.T("خاقان الأساطير", "Khan of Legends"), GameApp.T("القيصر المختار", "Chosen Emperor"), GameApp.T("الإمبراطور الناشئ", "Rising Emperor"), GameApp.T("الإمبراطور العظيم", "Great Emperor"),
            GameApp.T("سيد الممالك", "Master of Kingdoms"), GameApp.T("فاتح القارات", "Conqueror of Continents"), GameApp.T("حاكم البحار", "Ruler of the Seas"), GameApp.T("قائد الفرسان", "Commander of Knights"), GameApp.T("سيد العناصر", "Master of Elements"),
            GameApp.T("المحارب الصنديد", "Valiant Warrior"), GameApp.T("البطل الكوني", "Cosmic Hero"), GameApp.T("حارس المجرة", "Galaxy Guardian"), GameApp.T("مدمر العوالم", "World Destroyer"), GameApp.T("سيد الفراغ", "Lord of the Void"),
            GameApp.T("الكيان الأزلي", "Eternal Entity"), GameApp.T("روح الحرب", "Spirit of War"), GameApp.T("نور الأساطير", "Light of Legends"), GameApp.T("ظلام العدم", "Darkness of Nothingness"), GameApp.T("سيد المعارك", "Master of Battles"),
            GameApp.T("الحاكم المطلق", "Absolute Ruler"), GameApp.T("بطل الأبطال", "Hero of Heroes"), GameApp.T("سيد الملوك", "Lord of Kings"), GameApp.T("أسطورة الأساطير", "Legend of Legends"), GameApp.T("صانع المجد", "Maker of Glory"),
            GameApp.T("سيد الخلود", "Lord of Immortality"), GameApp.T("نجم الشمال", "North Star"), GameApp.T("شمس الحقيقة", "Sun of Truth"), GameApp.T("إمبراطور الأبدية", "Emperor of Eternity"), GameApp.T("أعظم أساطير العالم", "Greatest Legend of the World")
    };

    public static void openPrestigeUI() {
        PlayerData p = GameApp.player;
        int currentRank = p.prestigeLevel;
        int nextRank = currentRank + 1;
        if (nextRank > PRESTIGE_RANKS.length) {
            U.alert(GameApp.uiCtx(), GameApp.T("الرتب الملكية", "Royal Ranks"),
                    GameApp.T("تهانينا! لقد وصلت إلى أعلى رتبة ملكية ممكنة:", "Congratulations! You have reached the highest possible royal rank:") + PRESTIGE_RANKS[PRESTIGE_RANKS.length - 1],
                    GameApp.T("موافق", "OK"), null);
            return;
        }
        double baseCost = 100000000;
        long nextCost = (long) Math.floor(baseCost * Math.pow(1.5, nextRank - 1));
        String nextTitle = PRESTIGE_RANKS[nextRank - 1];
        int bonusPower = 500 + (nextRank * 100);
        String currentTitle = currentRank == 0 ? GameApp.T("لا يوجد", "None") : PRESTIGE_RANKS[currentRank - 1];
        String msg = GameApp.T("الرتبة الحالية:", "Current Rank:") + currentTitle +
                GameApp.T("\nالرتبة القادمة:", "\nNext Rank:") + nextTitle +
                GameApp.T("\n\nالمتطلبات:\n- الذهب المطلوب:", "\n\nRequirements:\n- Required Gold:") + NumberUtil.formatNumber(nextCost) +
                GameApp.T("\n\nالمميزات عند الترقية:\n- لقب ملكي جديد يظهر للجميع.\n- زيادة دائمة في القوة الإجمالية بمقدار", "\n\nUpgrade Benefits:\n- A new royal title visible to everyone.\n- A permanent increase in total power by") + bonusPower +
                GameApp.T(".\n- هالة ملكية خاصة في ملفك الشخصي.", ".\n- A special royal aura on your profile.");
        AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
        b.setTitle(GameApp.T("نظام الرتب الملكية", "Royal Ranks System"));
        b.setView(U.msg(msg));
        b.setPositiveButton(GameApp.T("ترقية الآن", "Upgrade Now"), (d, w) -> {
            if (p.gold >= nextCost) {
                p.gold -= nextCost;
                p.prestigeLevel = nextRank;
                p.prestigeTitle = nextTitle;
                int strGain = (int) Math.floor(bonusPower / 15.0);
                int endGain = (int) Math.floor(bonusPower / 30.0);
                int hpGain = (int) Math.floor(bonusPower / 2.0);
                p.stats.strength += strGain;
                p.stats.endurance += endGain;
                p.stats.maxHp += hpGain;
                p.stats.hp = Math.max(p.stats.hp, p.stats.maxHp);
                SaveSystem.saveAndRefresh();
                GameApp.sound.playSnd("reward.mp3");
                U.alert(GameApp.uiCtx(), GameApp.T("تهانينا!", "Congratulations!"), GameApp.T("لقد ارتقيت إلى رتبة:", "You have ascended to the rank:") + nextTitle + GameApp.T("\nأصبحت الآن أكثر هيبة وقوة!", "\nYou are now more majestic and powerful!"),
                        GameApp.T("عاش", "Long Live!"), null);
            } else {
                U.alert(GameApp.uiCtx(), null, GameApp.T("الذهب غير كافٍ! تحتاج إلى", "Not enough gold! You need") + NumberUtil.formatNumber(nextCost) + GameApp.T("ذهبة للترقية.", "gold to upgrade."),
                        GameApp.T("حسنا", "OK"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }
}
