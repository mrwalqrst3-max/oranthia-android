package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.MainActivity;
import com.lli.com.core.Db;
import com.lli.com.core.FriendEntry;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;
import com.lli.com.core.ServerStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingsSystem {

    public static void openSettingsUI() {
        final Context ctx = GameApp.uiCtx();
        final String[] base = {
                GameApp.T("شرح اللعبة", "Game Help"),
                GameApp.T("تغيير الاسم (15,000 ذهب)", "Change Name (15,000 gold)"),
                GameApp.T("قسم الصوت (المؤثرات والناطق)", "Sound Section (Effects & TTS)"),
                GameApp.T("الإشعارات", "Notifications"),
                GameApp.T("اللغات", "Languages"),
                GameApp.T("نسخ المعرف", "Copy ID"),
                GameApp.T("حالة السيرفر (زمن الاستجابة)", "Server Status (Ping)"),
                GameApp.T("تابعنا على السوشيال ميديا", "Follow Us on Social Media"),
                GameApp.T("الحسابات", "Accounts"),
                GameApp.T("تسجيل الخروج", "Log Out"),
                GameApp.T("حذف الحساب نهائياً", "Delete Account Permanently"),
                GameApp.T("تقرير الأعطال", "Crash Report"),
                GameApp.T("الشروط وسياسة الخصوصية", "Terms & Privacy Policy")
        };
        final boolean isDevL = GameApp.player != null && (GameApp.player.isDev || GameApp.player.adminData);
        final List<String> optL = new ArrayList<>();
        for (String s : base) optL.add(s);
        // Online/offline visibility is staff-only; regular players always show as online.
        final int hideStatusIdx = isDevL ? optL.size() : -1;
        if (isDevL) {
            optL.add(GameApp.player.hideOnline
                    ? GameApp.T("إظهار حالتي (للمطورين)", "Show My Status (Devs)")
                    : GameApp.T("إخفاء حالتي (للمطورين)", "Hide My Status (Devs)"));
        }
        if (isDevL) optL.add(GameApp.player.leaderVisible
                ? GameApp.T("إخفائي من قائمة الصدارة", "Hide Me from the Leaderboard")
                : GameApp.T("إظهاري في قائمة الصدارة", "Show Me on the Leaderboard"));
        final String[] opts = optL.toArray(new String[0]);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الإعدادات", "Settings"));
        b.setItems(opts, (d, i) -> {
            if (isDevL && i == optL.size() - 1) {
                GameApp.player.leaderVisible = !GameApp.player.leaderVisible;
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.player.leaderVisible
                        ? GameApp.T("سوف تظهر في قائمة الصدارة الآن.", "You will now appear on the leaderboard.")
                        : GameApp.T("تم إخفاؤك من قائمة الصدارة.", "You are now hidden from the leaderboard."), GameApp.T("حسنا", "OK"), v -> openSettingsUI());
                return;
            }
            if (hideStatusIdx >= 0 && i == hideStatusIdx) {
                GameApp.player.hideOnline = !GameApp.player.hideOnline;
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.player.hideOnline
                        ? GameApp.T("تم إخفاء حالتك. لن يظهر اسمك في قائمة المتصلين.", "Your status is hidden. You will not appear in the online list.")
                        : GameApp.T("أصبحت حالتك ظاهرة الآن.", "Your status is now visible."), GameApp.T("حسنا", "OK"), v -> openSettingsUI());
                return;
            }
            switch (i) {
                case 0: openHelpUI(); break;
                case 1: rename(); break;
                case 2: openSoundUI(); break;
                case 3: ArenaSystem.openNotifSettingsUI(); break;
                case 4: openLanguageUI(); break;
                case 5: copyId(); break;
                case 6: serverStatus(); break;
                case 7: social(); break;
                case 8: openAccountsManager(); break;
                case 9: logout(); break;
                case 10: deleteAccount(); break;
                case 11: viewCrashReport(); break;
                case 12: openLegalUI(); break;
            }
        });
        b.setPositiveButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void openLanguageUI() {
        final Context ctx = GameApp.uiCtx();
        final String[] opts = {
                GameApp.T("العربية", "Arabic") + (GameApp.isEn ? "" : ""),
                GameApp.T("الإنجليزية", "English") + (GameApp.isEn ? "" : "")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("اللغة", "Language"));
        b.setItems(opts, (d, i) -> {
            boolean en = i == 1;
            GameApp.setLanguage(en);
            U.alert(ctx, GameApp.T("اللغة", "Language"),
                    GameApp.T("تم تغيير اللغة. سيتم إعادة تشغيل الواجهة...", "Language changed. Restarting the interface..."),
                    GameApp.T("حسنا", "OK"), v -> MainActivity.inst.recreate());
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static String rateLabel(float r) {
        if (r <= 0.6f) return GameApp.T("بطيء (0.5x)", "Slow (0.5x)");
        if (r >= 2f) return GameApp.T("سريع جداً (2x)", "Very Fast (2x)");
        if (r >= 1.5f) return GameApp.T("سريع (1.5x)", "Fast (1.5x)");
        return GameApp.T("عادي (1x)", "Normal (1x)");
    }

    private static void openSoundUI() {
        final Context ctx = GameApp.uiCtx();
        final String[] opts = {
                GameApp.isMuted ? GameApp.T("تشغيل الصوت", "Enable Sound") : GameApp.T("كتم الصوت", "Mute Sound"),
                GameApp.T("الناطق (TTS):", "TTS:") + (GameApp.ttsEnabled ? GameApp.T("مفعل", "On") : GameApp.T("معطل", "Off")),
                GameApp.T("سرعة الناطق:", "TTS Speed:") + rateLabel(GameApp.ttsRate),
                GameApp.T("محرك النطق:", "TTS Engine:") + (GameApp.ttsEngine.isEmpty() ? GameApp.T("الافتراضي", "Default") : GameApp.ttsEngine),
                GameApp.T("تجربة الصوت والنطق", "Test Sound & TTS")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("قسم الصوت", "Sound Section"));
        b.setItems(opts, (d, i) -> {
            switch (i) {
                case 0: toggleMute(); openSoundUI(); break;
                case 1: toggleTts(); openSoundUI(); break;
                case 2: chooseTtsRate(); break;
                case 3: chooseTtsEngine(); break;
                case 4: testSound(); break;
            }
        });
        b.setPositiveButton(GameApp.T("رجوع", "Back"), (d, w) -> openSettingsUI());
        b.show();
    }

    private static void toggleTts() {
        GameApp.ttsEnabled = !GameApp.ttsEnabled;
        GameApp.prefs.edit().putBoolean("tts_enabled", GameApp.ttsEnabled).apply();
        if (GameApp.ttsEnabled) {
            GameApp.speakTts(GameApp.T("تم تفعيل الناطق", "TTS enabled"));
        } else {
            if (GameApp.tts != null) GameApp.tts.stop();
        }
    }

    private static void chooseTtsRate() {
        final Context ctx = GameApp.uiCtx();
        final String[] rates = {GameApp.T("بطيء (0.5x)", "Slow (0.5x)"), GameApp.T("عادي (1x)", "Normal (1x)"), GameApp.T("سريع (1.5x)", "Fast (1.5x)"), GameApp.T("سريع جداً (2x)", "Very Fast (2x)")};
        final float[] vals = {0.5f, 1.0f, 1.5f, 2.0f};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("سرعة الناطق", "TTS Speed"));
        b.setItems(rates, (d, i) -> {
            GameApp.ttsRate = vals[i];
            GameApp.prefs.edit().putFloat("tts_rate", GameApp.ttsRate).apply();
            GameApp.speakTts(GameApp.T("سرعة النطق الجديدة", "New speech speed"));
            openSoundUI();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), (d, w) -> openSoundUI());
        b.show();
    }

    private static void chooseTtsEngine() {
        final Context ctx = GameApp.uiCtx();
        final com.lli.com.core.Tts engineHolder = GameApp.tts;
        final java.util.List<android.speech.tts.TextToSpeech.EngineInfo> engines = engineHolder.getEngines();
        if (engines == null || engines.isEmpty()) {
            U.alert(ctx, null, GameApp.T("لا توجد محركات نطق إضافية على جهازك.", "No additional TTS engines found on your device."), GameApp.T("حسنا", "OK"), null);
            return;
        }
        final String[] names = new String[engines.size() + 1];
        final String[] ids = new String[engines.size() + 1];
        names[0] = GameApp.T("الافتراضي (نظام الجهاز)", "Default (system)");
        ids[0] = "";
        for (int i = 0; i < engines.size(); i++) {
            names[i + 1] = engines.get(i).label + "(" + engines.get(i).name + ")";
            ids[i + 1] = engines.get(i).name;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("اختر محرك النطق (TTS)", "Choose TTS Engine"));
        b.setItems(names, (d, i) -> {
            engineHolder.setEngine(ids[i]);
            GameApp.speakTts(GameApp.T("تم تغيير محرك النطق", "TTS engine changed"));
            openSoundUI();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), (d, w) -> openSoundUI());
        b.show();
    }

    private static void testSound() {
        GameApp.sound.playSnd("click.mp3");
        GameApp.sound.playSnd("reward.mp3");
        GameApp.speakTts(GameApp.T("هذا اختبار الصوت والنطق. اللعبة تعمل بشكل سليم.", "This is a sound and TTS test. The game is working properly."));
        U.alert(GameApp.uiCtx(), null, GameApp.T("تم تشغيل مؤثر صوتي ونطق تجريبي.", "A test sound and speech have been played."), GameApp.T("حسنا", "OK"), null);
    }

    private static void openHelpUI() {
        final Context ctx = GameApp.uiCtx();
        final String[] parts = {
                GameApp.T("نظرة عامة على اللعبة", "Game Overview"),
                GameApp.T("الشخصية والتطوير", "Character & Progression"),
                GameApp.T("المعدات والمستودع", "Equipment & Inventory"),
                GameApp.T("المدينة والأنظمة المالية", "City & Financial Systems"),
                GameApp.T("الاستكشاف والمغامرة", "Exploration & Adventure"),
                GameApp.T("عالم الزومبي", "Zombie World"),
                GameApp.T("الرتب الملكية (الهيبة)", "Royal Ranks (Prestige)"),
                GameApp.T("القبائل والمجتمع", "Tribes & Community"),
                GameApp.T("ساحة المبارزة", "Duel Arena"),
                GameApp.T("الزعيم العالمي والأحداث", "World Boss & Events"),
                GameApp.T("الأمان والـ ID والحسابات", "Security, ID & Accounts"),
                GameApp.T("ساحة المبارزة والدردشة الصوتية", "Duel Arena & Voice Chat"),
                GameApp.T("الهدايا بين اللاعبين والمراسلات", "Gifts & Player Mail"),
                GameApp.T("الإبلاغ عن المخالفات والعقوبات", "Reporting, Monitoring & Penalties"),
                GameApp.T("نظام القوة والمعدات", "Power & Gear System"),
                GameApp.T("الصناديق والمفاتيح", "Boxes & Keys"),
                GameApp.T("الجرعات والرسائل الخاصة", "Potions & Private Messages"),
                GameApp.T("نصائح عامة للمبتدئين", "General Tips for Beginners")
        };
        final String[] texts = {
                GameApp.T("نظرة عامة على اللعبة:\nاللعبة مقسمة إلى 5 تبويبات رئيسية في أعلى الشاشة، وكل تبويب يضم أنظمة معينة:\n1) الشخصية: تفاصيل بطلك — الإحصائيات، نقاط التطوير، العرق، المعدات، الحقيبة، الرتب الملكية.\n2) المدينة: أين تصرف وتستثمر — البنك، المتجر الملكي، متجر الألماس، المزاد، عجلة الحظ، الهدية اليومية، سوق اللاعبين.\n3) المغامرة: أين تكسب — الاستكشاف، عالم الزومبي، برج التحدي، الزعيم العالمي، المبارزات، المهام.\n4) المجتمع: أين تلعب مع الآخرين — الدردشة، قائمة الصدارة، الأصدقاء، القبيلة، ساحة المبارزة أونلاين.\n5) الإدارة: قسم خاص بالمشرفين والمطورين فقط.\n\nكيف تبدأ في أول يوم؟\n- افتح \"الهدية اليومية\" في المدينة لتحصل على مكافأتك.\n- لف عجلة الحظ مجاناً مرة واحدة يومياً.\n- اذهب لتبويب المغامرة واضغط \"استكشاف المنطقة الحالية\" لتقتل وحوشاً وتكسب الخبرة والذهب.\n- وزّع نقاط التطوير من تبويب الشخصية لتقوى.\n- أودع ذهبك في البنك ليكبر بالفائدة.",
                        "Game Overview:\nThe game is split into 5 main tabs at the top of the screen, and each tab contains specific systems:\n1) Character: everything about your hero - stats, upgrade points, race, gear, bag, royal ranks.\n2) City: where you spend and invest - the bank, royal shop, diamond shop, auction, wheel of fortune, daily gift, player market.\n3) Adventure: where you earn - exploration, zombie world, challenge tower, world boss, duels, quests.\n4) Community: where you play with others - chat, leaderboard, friends, tribe, online duel arena.\n5) Admin: a section for moderators and developers only.\n\nHow to start on your first day?\n- Open the \"Daily Gift\" in the city to claim your reward.\n- Spin the Wheel of Fortune for free once a day.\n- Go to the Adventure tab and press \"Explore Current Zone\" to fight monsters and earn XP and gold.\n- Spend your upgrade points from the Character tab to get stronger.\n- Deposit your gold in the bank so it grows with interest."),

                GameApp.T("الشخصية والتطوير:\n- تكسب الخبرة من قتل الوحوش والمهام والاستكشاف، وكلما امتلأ شريط الخبرة ترتفع مستواك.\n- مقدار الخبرة المطلوب للمستوى التالي يزداد كلما ارتفع مستواك.\n\nماذا تحصل عند كل لفل أب؟\n• +500 صحة قصوى\n• +5 هجوم\n• +3 دفاع\n• 4 نقاط تطوير\n\nكيف توزع نقاط التطوير؟\n- من \"إحصائياتك وتطوير نقاط البطل\" داخل تبويب الشخصية، كل نقطة تمنحك:\n • هجوم +5\n • دفاع +3\n • صحة +20\n • رشاقة +2\n • حظ +2\n\nمثال عملي:\n- وزعت 20 نقطة في الهجوم عند مستوى 10 → هجومك الإضافي +100.\n- إعادة توزيع النقاط متاحة مقابل 5 ألماس (تبويب الشخصية) وتُرجِع كل النقاط مع الحفاظ على معداتك.\n- قسم \"أخبار الشخصية\" يعرض آخر مستجدات عالمك (معارك، مكافآت، أماكن).",
                        "Character & Progression:\n- You earn XP from killing monsters, quests and exploration, and when the XP bar fills up you level up.\n- The XP required for the next level keeps growing as you level up.\n\nWhat do you get at each level up?\n- +500 max HP\n- +5 attack\n- +3 defense\n- 4 upgrade points\n\nHow to spend upgrade points?\n- From \"Your Stats & Hero Points\" in the Character tab, each point gives you:\n - Attack +5\n - Defense +3\n - HP +20\n - Agility +2\n - Luck +2\n\nPractical example:\n- You spent 20 points on attack at level 10 -> +100 bonus attack.\n- Redistributing points costs 5 diamonds (Character tab) and restores all points while keeping your gear.\n- The \"Character News\" section shows the latest updates in your world (battles, rewards, locations)."),

                GameApp.T("المعدات والمستودع:\n- تجمع المعدات من الوحوش والصناديق والاستكشاف وعالم الزومبي والمتاجر والمزاد.\n\nأنواع المعدات حسب الندرة (من الأضعف للأقوى):\n- عادي ← نادر ← ملحمي ← أسطوري ← خرافي.\n- المعدات الخرافية تعطي أقوى تعزيز لإحصائياتك.\n\nماذا يمكنك أن تفعل من \"الحقيبة والمعدات\"؟\n- التجهيز: جهّز أفضل قطعة لكل مكان (سلاح/درع).\n- البيع: بيع القطع الزائدة مقابل ذهب.\n- الترقية: ارفع مستوى القطعة لتزداد قوتها.\n\nمثال عملي:\n- سيف أسطوري يرفع هجومك من 200 إلى 350 → ضررك المتوقع (هجوم × 1.5) يرتفع تلقائياً.\n- القوة الإجمالية تظهر في الشريط العلوي وفي قوائم الصدارة.",
                        "Equipment & Inventory:\n- Gear is collected from monsters, boxes, exploration, the zombie world, shops and auction.\n\nRarity tiers (weakest to strongest):\n- Common <- Rare <- Epic <- Legendary <- Mythical.\n- Mythical gear gives the strongest stat boosts.\n\nWhat can you do from \"Bag & Equipment\"?\n- Equip: put the best piece on each slot (weapon/armor).\n- Sell: sell extra pieces for gold.\n- Upgrade: raise a piece's level so it gets stronger.\n\nPractical example:\n- A legendary sword raises your attack from 200 to 350 -> your expected damage (attack x 1.5) rises automatically.\n- Total power appears in the top bar and leaderboards."),

                GameApp.T("المدينة والأنظمة المالية:\n- البنك الملكي: أودع ذهبك واكسب فائدة يومية 8%. كلما أودعت مبلغاً أكبر كبرت الفائدة. مثال: أودعت 1,000,000 ذهب → تستلم 80,000 ذهب فائدة في اليوم التالي.\n- المتجر الملكي الشامل: اشترِ معدات وعناصر بالذهب والكريستال.\n- متجر الألماس الأسطوري: عناصر حصرية تُشترى بالألماس.\n- المزاد الملكي: اعرض عناصرك للبيع للاعبين، أو اشترِ من مزاداتهم وتنافس عليها.\n- عجلة الحظ: لفها مجاناً مرة يومياً لجوائز مفاجئة (صناديق، مفاتيح، عملات).\n- الهدية اليومية: ادخل كل يوم دون انقطاع لترفع سلسلة المكافآت — ترك يوم واحد يكسر السلسلة.\n- صندوق الغموض: افتحه مقابل عملة للحصول على جوائز أسطورية.\n- ساحة التأمل (AFK): اجمع موارد تلقائياً أثناء غيابك — ادخل واجمع حصادك.\n- سوق اللاعبين: تعامل مباشر بين اللاعبين بملاحظة العرض والطلب.\n\nنصيحة: لا تدع ذهبك مكدساً — أودعه في البنك حتى يعمل لك.",
                        "City & Financial Systems:\n- Royal Bank: deposit your gold and earn an 8% daily interest. The bigger the deposit, the bigger the interest. Example: you deposit 1,000,000 gold -> you receive 80,000 gold interest the next day.\n- Grand Royal Shop: buy gear and items with gold and crystals.\n- Legendary Diamond Shop: exclusive items bought with diamonds.\n- Royal Auction: list your items for sale or bid on other players' auctions.\n- Wheel of Fortune: spin free once a day for surprise prizes (boxes, keys, currencies).\n- Daily Gift: log in every day without missing to raise the reward streak - missing one day resets it.\n- Mystery Box: open it with a currency to get legendary prizes.\n- Meditation Square (AFK): collect resources automatically while away - enter and harvest.\n- Player Market: direct trading between players.\n\nTip: do not let your gold sit idle - deposit it in the bank so it works for you."),

                GameApp.T("الاستكشاف والمغامرة:\n- من تبويب المغامرة اضغط \"استكشاف المنطقة الحالية\" وستقاتل وحوش منطقتك.\n- كلما انتقلت لمنطقة جديدة: الوحوش تصبح أقوى لكن الخبرة والذهب يزدادان أكثر.\n- السفر لمنطقة جديدة يكلف 27 ذهباً.\n\nأحداث عشوائية أثناء الاستكشاف:\n- تجار: اشترِ منهم عروضاً قد تكون ثمينة.\n- لصوص: يمكنهم سرقتك — كن حذراً وقرر بذكاء.\n- صناديق كنوز: قد تجدها وتبقى محفوظة حتى تفتحها بمفتاح.\n\nأنظمة إضافية:\n- برج التحدي المظلم: تحدٍّ مستمر، كل طابق أصعب من السابق، مع مكافآت عند مراحل معينة.\n- نقابة المرتزقة (المهام): أنجز المهام المطلوبة لتحصل على جوائز إضافية.\n- الصندوق المكتشف: كل الصناديق التي وجدتها وما زالت مغلقة تجدها في قسم الصناديق.\n\nنصيحة: ارفع مستواك ومعداتك أولاً قبل السفر لمناطق أعلى.",
                        "Exploration & Adventure:\n- From the Adventure tab press \"Explore Current Zone\" and you will fight your zone's monsters.\n- The higher the zone, the tougher the monsters but the more XP and gold they give.\n- Traveling to a new zone costs 27 gold.\n\nRandom events during exploration:\n- Traders: buy their offers - some may be valuable.\n- Thieves: they can rob you - be careful and decide wisely.\n- Treasure boxes: you may find them and they stay saved until you open them with a key.\n\nExtra systems:\n- Dark Challenge Tower: a continuous challenge, every floor is harder, with rewards at milestones.\n- Mercenary Guild (quests): complete the required quests to get extra prizes.\n- Discovered Box: all your found but still closed boxes are in the boxes section.\n\nTip: level up and upgrade your gear first before traveling to higher zones."),

                GameApp.T("عالم الزومبي:\n- نظام تحكم خاص بالسحب: اسحب للأعلى للمشي، ولليمين/اليسار للدوران.\n- اقتل الزومبي لتحصل على أسلحة متطورة وجوائز قيمة.\n- من عالم الزومبي تحصل على عناصر فتح الأعراق (قلب الزومبي الملكي، دم الزومبي الأزلي، نواة الزومبي النووية وغيرها).\n- انتبه من الوحوش الخرافية الذهبية — نادرة جداً وغنائمها ضخمة.\n- تقدمك (موقعك وعدد القتلى) يُحفظ تلقائياً عند الخروج.\n- مثال: لفتح عرق التنانين تحتاج \"قلب الزومبي الملكي\" ×5 — اذهب لاصطياد الزومبي الملكيين.",
                        "Zombie World:\n- Special drag controls: swipe up to walk, swipe left/right to rotate.\n- Kill zombies to get advanced weapons and valuable prizes.\n- The zombie world drops race-unlock items (Royal Zombie Heart, Eternal Zombie Blood, Nuclear Zombie Core, and more).\n- Beware of golden mythical monsters - they are very rare with huge loot.\n- Your progress (position and kill count) is saved automatically on exit.\n- Example: to unlock the Dragons race you need 5 Royal Zombie Hearts - go hunt royal zombies."),

                GameApp.T("الرتب الملكية (الهيبة):\n- نظام خاص للأثرياء والقوة: الرتبة الأولى تكلف 100 مليون ذهب.\n- يوجد 50 رتبة ملكية، وكل ترقية تكلف أكثر من سابقتها (التكلفة تتضاعف تقريباً ×1.5 كل رتبة).\n- كل رتبة تمنحك لقباً ملكياً يظهر بجانب اسمك أمام الجميع، وترفع قوتك الإجمالية (هجوم/دفاع/صحة) بشكل دائم.\n- اللقب ليس للزينة فحسب — فقوتك وحضورك في الصدارة يرتفعان مع كل رتبة.\n- مثال: ابدأ من \"فارس متواضع\" وصولاً إلى أعلى الألقاب.\n- نصيحة: قبل إنفاق مبلغ كبير على الرتبة، احفظ جزءاً من ذهبك في البنك ليظل رأس مالك ينمو.",
                        "Royal Ranks (Prestige):\n- A system for the rich and powerful: the first rank costs 100 million gold.\n- There are 50 royal ranks, and each upgrade costs more than the previous (roughly x1.5 per rank).\n- Every rank grants a royal title shown next to your name for everyone to see, and permanently raises your total power (attack/defense/HP).\n- The title is not just for decoration - your power and leaderboard presence rise with each rank.\n- Example: start from \"Humble Knight\" up to the highest titles.\n- Tip: before spending a huge amount on a rank, keep part of your gold in the bank so your capital keeps growing."),

                GameApp.T("القبائل والمجتمع:\n- الدردشة: تحدث مع اللاعبين وتبادل الخبرات (يوجد مشرفون في القناة العامة يحافظون على الاحترام).\n- قائمة الصدارة: تنافس على صدارة القوة والمستوى والأغنى.\n- الأصدقاء: أضف أصدقاءك وادعمهم وتبادل الهدايا.\n- القبيلة: انضم لقبيلة أو أنشئها، وساهم بالذهب والجوهر لرفع مستوى القبيلة وفتح مهارات مشتركة تفيد كل الأعضاء.\n- سوق اللاعبين: اعرض عناصرك للبيع واشترِ من بقية اللاعبين.\n- مثال: الانضمام لقبيلة نشطة يسهل عليك الترقية ويوفر لك مساعدات أسرع.\n- نصيحة: كن محترماً في الدردشة — التعاون يفتح لك أبواباً أكثر من الخصام.",
                        "Tribes & Community:\n- Chat: talk with players and share tips (moderators are in the general channel to keep it respectful).\n- Leaderboard: compete for the top in power, level and riches.\n- Friends: add friends, support them and exchange gifts.\n- Tribe: join or create a tribe, contribute gold and gems to raise the tribe level and unlock shared skills that benefit every member.\n- Player Market: list your items for sale and buy from other players.\n- Example: joining an active tribe makes upgrading easier and gets you faster help.\n- Tip: be respectful in chat - cooperation opens more doors than fighting."),

                GameApp.T("ساحة المبارزة:\n- المبارزة الاستراتيجية (وحوش): اختر استراتيجيتك وواجه وحوشاً قوية بأسلوب تحدٍّ مستمر.\n- ساحة المبارزة أونلاين: ابحث عن خصم حقيقي وأرسل له طلب مبارزة.\n- عند قبول الطلب تدخل \"ساحة الانتظار\" وتبقى فيها حتى يبدأ الدور الأول.\n- عند البدء تنتقل إلى \"ساحة المبارزة\" الحية:\n • شريط صحة لكل طرف + مؤشر يدل على من يحمل الدور (أنت أم الخصم).\n • في دورك اختر من بين 4 استراتيجيات متاحة، ويمكنك فتح الحقيبة لاستخدام الأدوات.\n • أي تحرك من الخصم يظهر على شاشتك فوراً (تنخفض الصحة، يتغير الدور).\n- الفائز يحصل على الجوائز، وتنتهي الساحة تلقائياً لحظة انتهاء المباراة.\n- مثال: إذا خطف الخصم زمام الهجوم ستلاحظ صحته وحالته تتحدث مباشرة — انتظر دورك وردّ باستراتيجية مضادة.",
                        "Duel Arena:\n- Strategic Duel (monsters): choose your strategy and face strong monsters in a continuous challenge.\n- Online Duel Arena: find a real opponent and send a duel request.\n- Once accepted you enter the \"Waiting Arena\" until the first turn starts.\n- When it starts you move to the live \"Duel Arena\":\n - A health bar for each side plus an indicator of who holds the turn (you or the opponent).\n - On your turn pick one of 4 strategies; you can also open your bag to use items.\n - Any opponent move appears on your screen instantly (health drops, turn label changes).\n- The winner gets the prizes, and the arena ends automatically when the match is over.\n- Example: if the opponent grabs the attack initiative you will see their health/state update live - wait for your turn to counter."),

                GameApp.T("الزعيم العالمي والأحداث:\n- يظهر الزعيم العالمي في أوقات محددة من تبويب المغامرة.\n- كل ضرر تسببه للزعيم يسجل في لوحة صدارة الزعيم.\n- أفضل المساهمين يحصلون على جوائز كبرى عند هزيمة الزعيم.\n- ركّز هجومك قبل موعد الزعيم لتحجز مكاناً بين الأفضل.\n- لا تنسَ الأحداث اليومية: عجلة الحظ، صندوق الغموض، الهدية اليومية.\n- مثال: دخلت معركة الزعيم وألحقت 5 ملايين ضرر → نوعيتك في الصدارة وستنال جوائز عند هزيمته.",
                        "World Boss & Events:\n- The world boss appears at scheduled times from the Adventure tab.\n- Every damage you deal to the boss is recorded on the boss leaderboard.\n- The best contributors get major rewards when the boss is defeated.\n- Boost your attack before the boss time to secure a spot among the top.\n- Do not forget the daily events: wheel of fortune, mystery box, daily gift.\n- Example: you joined the boss fight and dealt 5 million damage -> your rank is in the leaderboard and you will get rewards when it falls."),

                GameApp.T("الأمان والـ ID والحسابات:\n- الـ ID الخاص بك هو مفتاح حسابك الوحيد — لا تشاركه مع أي شخص أبداً مهما كان السبب.\n- حسابك يُرفع للسيرفر تلقائياً مع كل تغيير، وتستعيده من شاشة البداية بخيار \"استعادة حساب\".\n- تابعنا على السوشيال ميديا من الإعدادات واحصل على مكافأة مرة واحدة: 100,000 ذهب + 50 كريستال + 20 ألماس.\n- الحسابات: يمكنك إنشاء حتى 3 حسابات إضافية (بمقابل: 100 مليون ذهب + 150 كريستال + 65 ألماس) والتبديل بينها.\n- تغيير الاسم متاح من الإعدادات مقابل 15,000 ذهب.\n- مثال: احفظ الـ ID في مكان آمن خارج اللعبة — لو فقدته أو شاركته فلن تتمكن الإدارة من استعادته لك.\n- تنبيه: الإدارة لن تطلب منك الـ ID أبداً داخل اللعبة — أي شخص يطلب منه فهو محتال.",
                        "Security, ID & Accounts:\n- Your ID is the only key to your account - never share it with anyone for any reason.\n- Your account is uploaded to the server automatically with every change; you can restore it from the start screen with \"Restore Account\".\n- Follow us on social media from Settings and get a one-time reward: 100,000 gold + 50 crystals + 20 diamonds.\n- Accounts: you can create up to 3 extra accounts (cost: 100 million gold + 150 crystals + 65 diamonds) and switch between them.\n- Changing your name is available from Settings for 15,000 gold.\n- Example: save your ID somewhere safe outside the game - if you lose or share it, the staff cannot recover it for you.\n- Warning: the staff will never ask for your ID inside the game - anyone who asks for it is a scammer."),

                GameApp.T("ساحة المبارزة والدردشة الصوتية المباشرة:\n- من تبويب المجتمع اختر \"ساحة المبارزة أونلاين\" وابحث عن لاعب حقيقي، أو قاتل وحوشاً قوية بالمبارزة الاستراتيجية.\n- داخل الساحة يوجد زر \"تحدث مباشرة (ميكروفون)\": اضغطه مرة للتكلم ومرة أخرى للإيقاف.\n- صوتك ينتقل فوراً لجميع اللاعبين المتواجدين في نفس الساحة عبر اتصال مباشر لاسلكي من جهاز لجهاز (اتصال فائق السرعة).\n- يمكنك كتم صوت أي لاعب مزعج من سجل الساحة.\n- الصوت مقيد بالقوانين: لا أصوات مسيئة أو إزعاج، ومن يخالف يُعاقب.\n- مثال: اضغط زر الميكروفون وقل \"حاضر\" وسيسمعك كل اللاعبين في الساحة فوراً.",
                        "Duel Arena & Live Voice Chat:\n- From the Community tab choose \"Online Duel Arena\" and search for a real player, or fight strong monsters in Strategic Duel.\n- Inside the arena there is a \"Talk Live (Mic)\" button: press once to talk and again to stop.\n- Your voice connects instantly to all players in the same arena via a direct peer-to-peer link (ultra-low latency).\n- You can mute any annoying player from the arena log.\n- Voice is subject to the rules: no offensive sounds or harassment; violators are punished.\n- Example: press the mic button and say \"ready\" - everyone in the arena hears you instantly."),

                GameApp.T("الهدايا بين اللاعبين والمراسلات:\n- من قائمة اللاعبين أو الأصدقاء أو ساحة المبارزة يمكنك إرسال هدية.\n- أنواع الهدايا: ذهب، كريستال، ألماس، أو عنصر من حقيبتك تختاره بنفسك.\n- عند إهداء عنصر: يُشترط أن تملكه فعلاً، ويُخصم من حقيبتك فوراً ويصل للمستلم.\n- يصل اللاعب المهدى إليه إشعار في صندوق \"المراسلات\" (Inbox) ويمكنه قبول الهدية.\n- الهدايا غير المستلمة تبقى محفوظة حتى قبولها أو انتهاء مدتها.\n- مثال: اهدِ صديقك سيفاً أسطورياً من حقيبتك وسيظهر له فوراً في رسائله.",
                        "Gifts & Player Mail:\n- From the players list, friends or the duel arena you can send a gift.\n- Gift types: gold, crystals, diamonds, or an item you pick yourself from your bag.\n- When gifting an item: you must actually own it, it is deducted from your bag instantly and reaches the receiver.\n- The receiver gets a notification in their mail box (Inbox) and can accept the gift.\n- Unreceived gifts stay saved until accepted or their time expires.\n- Example: gift your friend a legendary sword from your bag and it appears in their mail instantly."),

                GameApp.T("الإبلاغ عن المخالفات والعقوبات:\n- البلاغات تُقبل فقط بسبب حقيقي ومع دليل قاطع (لقطة شاشة واضحة، مقطع صوتي/فيديو، أو سجل داخل اللعبة).\n- بلاغ بدون دليل يُرفض، ومن يتكرر منه البلاغ الكاذب بقصد الإضرار يُعاقب.\n- عند قبول بلاغ: يوضع اللاعب المتهم تحت المراقبة من 24 ساعة حتى أسبوعين وتُفحص سجلاته.\n- نظام العقوبات تدريجي: تحذير ← كتم/حظر دردشة ← تجميد مؤقت ← حظر نهائي و/أو حظر جهاز.\n- الغش واستغلال الأخطاء: مصادرة المكاسب غير المشروعة + عقوبة.\n- إذا ثبتت براءتك من بلاغ كاذب تُرفع العقوبة فوراً.\n- يمكنك طلب مراجعة/عفو واحد خلال 30 يوماً عبر التواصل مع الإدارة (تيليجرام).",
                        "Reporting, Monitoring & Penalties:\n- Reports are accepted only with a genuine reason and conclusive evidence (a clear screenshot, an audio/video clip, or an in-game log).\n- A report without evidence is rejected; repeated false reports made to harm others are punishable.\n- When a report is accepted: the accused player is monitored from 24 hours up to two weeks and their logs are examined.\n- The penalty system is escalating: warning -> mute/chat ban -> temporary freeze -> permanent ban and/or device ban.\n- Cheating and exploiting bugs: confiscation of illegitimate gains + penalty.\n- If you are proven innocent of a false report, the penalty is lifted immediately.\n- You may submit one review/amnesty request within 30 days by contacting the staff (Telegram)."),

                GameApp.T("نظام القوة والمعدات:\n- القوة الكلية تُحسب من إحصائياتك (هجوم، دفاع، صحة، رشاقة، حظ) ومستواك ومعداتك وعرقك ورتبك الملكية.\n- الضرر المتوقع = الهجوم × 1.5، والدفاع يقلل الضرر المتلقى.\n- الرشاقة تمنحك سرعة، والحظ يرفع فرص الغنائم والضربات الحرجة.\n- أنواع المعدات حسب الندرة: عادي، نادر، ملحمي، أسطوري، خرافي.\n- المعدات الخرافية والأعلى مستوى تعطي أقوى تعزيز.\n- من \"الحقيبة والمعدات\" يمكنك: التجهيز، البيع، وترقية المعدات.\n- مثال: سيف خرافي يرفع هجومك من 200 إلى 500 → ضررك المتوقع يرتفع تلقائياً وتتفوق في الصدارة.",
                        "Power & Gear System:\n- Total power is calculated from your stats (attack, defense, HP, agility, luck), level, gear, race and royal ranks.\n- Expected damage = attack x 1.5, and defense reduces damage taken.\n- Agility gives you speed, and luck raises the chance of loot and critical hits.\n- Gear rarity tiers: common, rare, epic, legendary, mythical.\n- Mythical and higher-level gear give the strongest boosts.\n- From \"Bag & Equipment\" you can: equip, sell, and upgrade gear.\n- Example: a mythical sword raises your attack from 200 to 500 -> your expected damage rises automatically and you lead the leaderboard."),

                GameApp.T("الصناديق والمفاتيح:\n- الصناديق المكتشفة تظهر في \"الصندوق المكتشف\" (قسم الصناديق) وتبقى محفوظة حتى تفتحها.\n- لفتح معظم الصناديق تحتاج مفاتيح تُربح من الاستكشاف والأحداث وعالم الزومبي والوحدات.\n- صندوق الغموض (في المدينة) يُفتح مقابل عملة ويحوي جوائز أسطورية.\n- عجلة الحظ تمنحك صناديق ومفاتيح يومياً.\n- افتح صناديقك مبكراً فاغتنام الجوائز يساعدك على التقدم أسرع.\n- مثال: وجدت صندوق كنوز في الاستكشاف → افتحه بمفتاح لتحصل على ذهب ومعدات.",
                        "Boxes & Keys:\n- Discovered boxes appear in \"Discovered Box\" (Boxes section) and stay saved until you open them.\n- Opening most boxes requires keys earned from exploration, events, the zombie world and monsters.\n- Mystery Box (in the city) is opened with a currency and contains legendary prizes.\n- The Wheel of Fortune gives you boxes and keys daily.\n- Open your boxes early - grabbing prizes helps you progress faster.\n- Example: you found a treasure box while exploring -> open it with a key for gold and gear."),

                GameApp.T("الجرعات والرسائل الخاصة:\n- الجرعات: تُشترى من قسم \"الجرعات\" في المتجر الملكي وتُحفظ في الحقيبة (لا تُفعل تلقائياً).\n- لاستخدامها افتح الحقيبة وقسم الجرع واختر \"شرب الجرعة\".\n- أنواع الجرعات: هجوم، دفاع، رشاقة، حظ، ولكل جرعة قيمة تأثير.\n- بعض الجرعات تعمل بالوقت (مدة بالدقائق مع عدّاد مباشر يظهرها) وبعضها بعدد معرّات (تنتهي بعد عدد محدد من المعارك).\n- الجرعة الزمنية تنتهي تلقائياً فور انتهاء مدتها، والجرعة المعرّية تنقص تلقائياً بعد كل معركة.\n- صدارة \"أقوى المنتصرين\" تعرض أبطال الساحة (الأعلى انتصارات) من سجل الميداليات.\n- رسالة خاصة لأي لاعب: اختر \"رسالة للاعب معين (بحث بالاسم)\" من الدردشة حتى لو لم يكن صديقاً لك.\n- عند وصول رسالة خاصة يصدر صوت مميز مختلف عن صوت الدردشة العامة، وتظهر الهداية مع إشعار.\n- قائمة \"اللاعبون المتصلون\" تشمل من يظهروا حتى وإن لم يكونوا أصدقاءك، مع علامة درع 🛡 للمشرفين المتصلين.",
                        "Potions & Private Messages:\n- Potions: buy them from the \"Potions\" section of the royal shop and they are stored in your bag (not auto-activated).\n- To use one, open the bag and the Potions category, then choose \"Drink Potion\".\n- Potion types: attack, defense, agility, luck; each potion has an effect value.\n- Some potions are time-based (duration in minutes with a live countdown) and some are battle-based (last for a set number of battles).\n- A time potion expires automatically when its timer ends; a battle potion ticks down after each battle.\n- The \"Top Winners\" leaderboard shows the arena champions (most wins) from the medals record.\n- Private message to any player: choose \"Message a Player (Search by Name)\" from the chat even if they are not your friend.\n- A private message arrival plays a distinctive sound different from the public chat sound, plus a toast notification.\n- The \"Online Players\" list includes users even if they are not your friends, with a shield 🛡 badge on connected admins."),

                GameApp.T("نصائح عامة للمبتدئين:\n- اجمع الهدية اليومية كل يوم دون انقطاع لرفع سلسلة المكافآت.\n- استثمر ذهبك في البنك (فائدة يومية 8%) بدلاً من تركها مكدسة.\n- وازن نقاط التطوير بين الهجوم والصحة حسب أسلوب لعبك.\n- رقِّ معداتك واحمل أفضل قوة قبل السفر لمناطق أعلى.\n- انضم لقبيلة نشطة للمساعدة والمزايا المشتركة.\n- تابعنا على السوشيال ميديا من الإعدادات واحصل على مكافأة اجتماعية مرة واحدة.\n- من موقع الاستكشاف واجه وحوشاً لتكسب خبرة وذهباً ثم وسع حقيبتك.\n- لا تشارك الـ ID مع أي شخص مهما كان السبب.",
                        "General Tips for Beginners:\n- Collect the daily gift every day without missing to raise the reward streak.\n- Invest your gold in the bank (8% daily interest) instead of leaving it idle.\n- Balance your upgrade points between attack and HP based on your play style.\n- Upgrade your gear and carry the best power before traveling to higher zones.\n- Join an active tribe for help and shared benefits.\n- Follow us on social media from Settings and get a one-time social reward.\n- Fight monsters from the exploration screen to earn XP and gold, then expand your bag.\n- Never share your ID with anyone for any reason.")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("دليل الأساطير الشامل", "The Complete Legends Guide"));
b.setItems(parts, (d, i) -> showDocPage(ctx, parts[i], texts[i]));
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void rename() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        if (p.gold < 15000) {
            U.alert(ctx, null, GameApp.T("الذهب غير كاف!", "Not enough gold!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        final EditText ed = U.edit(ctx, GameApp.T("الاسم الجديد", "New Name"));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الاسم الجديد", "New Name"));
        b.setView(ed);
        b.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (d, w) -> {
            String nn = ed.getText().toString().trim();
            if (nn.isEmpty()) return;
            p.name = nn;
            p.gold -= 15000;
            SaveSystem.saveAndRefresh();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void viewCrashReport() {
        final Context ctx = GameApp.uiCtx();
        String log = GameApp.T("===== سجل التجمّد (ANR) =====", "===== Freeze Log (ANR) =====") + "\n" + GameApp.readAnrLog()
                + "\n\n" + GameApp.T("===== أخطاء كراش =====", "===== Crash Errors =====") + "\n" + GameApp.readCrashLog();
        if (log.length() > 8000) log = log.substring(log.length() - 8000);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("تقرير الأعطال", "Crash Report"));
        b.setView(U.msg(log));
        b.setPositiveButton(GameApp.T("حسنا", "OK"), null);
        b.show();
    }

    public static String termsText() {
        return GameApp.T(
                "شروط الاستخدام\n\n" +
                        "1) الحساب والـ ID:\n- الـ ID هو مفتاح حسابك الوحيد، ولا يجوز مشاركته أو بيعه أو تسليمه لأي شخص.\n- أنت المسؤول الوحيد عن الحفاظ على سرية الـ ID. الإدارة لن تستطيع استعادة حساب تمت سرقته بسبب مشاركتك له.\n- يُسمح لنا بالوصول إلى عنوان IP الخاص بالأجهزة أو رؤيته لأغراض حظر المستخدمين الغشاشين وأغراض أخرى متعلقة بحماية اللعبة.\n\n" +
                        "2) السلوك داخل اللعبة:\n- يحظر السب أو الشتم أو التنمر في الدردشة أو أسماء الساحات أو القبائل.\n- يحظر الترويج أو الإعلان عن مواقع أو حسابات خارج اللعبة دون إذن الإدارة.\n- يحظر نشر روابط ضارة أو أي محتوى مسيء أو سياسي أو ديني يستفز الآخرين.\n- يحظر انتحال شخصية الإدارة أو المطورين أو أي لاعب آخر.\n- يحظر إغراق الدردشة برسائل متكررة (سبام) أو إزعاج اللاعبين.\n- يحظر إساءة استخدام الدردشة الصوتية في الساحات أو إرسال أصوات مسيئة أو مزعجة.\n\n" +
                        "3) الغش والاستغلال:\n- يحظر استخدام أي برامج أو تعديلات تمنحك ميزة غير عادلة.\n- يحظر استغلال الأخطاء البرمجية. من يكتشف خطأً ويستغله بدلاً من الإبلاغ عنه يُعاقب.\n- يحظر إنشاء حسابات متعددة للغش أو التلاعب بالجوائز (الغش بالحسابات الفرعية).\n- يحظر بيع أو تداول الـ IDs أو العملات أو العناصر مقابل أموال حقيقية.\n\n" +
                        "4) البلاغات (الإبلاغ عن المخالفات):\n- أي بلاغ ضد لاعب يجب أن يحمل سبباً حقيقياً وواضحاً.\n- لا يُقبل أي بلاغ دون دليل قاطع يثبت المخالفة (لقطة شاشة واضحة، أو مقطع صوتي/فيديو، أو سجل داخل اللعبة يثبتها).\n- عند استيفاء البلاغ للشروط يوضع اللاعب المتهم تحت المراقبة مدة تتراوح بين 24 ساعة وأسبوعين وتُراجع سجلاته بدقة قبل أي قرار.\n- للتحقق من البلاغات الصوتية يحق لأعضاء الإشراف الاستماع مباشرة إلى الدردشة الصوتية في الساحات (مراقبة غير مرئية دون كشف وجودهم) رغم وجود اللاعبين نفسهم هناك.\n- البلاغ الكاذب بقصد الإضرار بلاعب آخر أو تشويه سمعته يعاقب صاحبه بنفس جدول العقوبات.\n- تكرار البلاغات بلا أي دليل يُعتبر مضايقة للعبة وللإدارة ويُعاقب عليه.\n\n" +
                        "5) نظام العقوبات (تدريجي):\n- أول مخالفة بسيطة: تحذير كتابي واضح.\n- التكرار: كتم مؤقت أو حظر الدردشة (من ساعات حتى أيام).\n- المخالفات المتوسطة: تجميد الحساب مؤقتاً (سجن) من أيام حتى أسابيع حسب الخطورة.\n- الغش أو استغلال الأخطاء: مصادرة كل المكاسب غير المشروعة + تجميد مؤقت أو دائم.\n- الانتحال أو الاحتيال أو بيع الحسابات أو الـ IDs: حظر نهائي للحساب.\n- التكرار بعد العقوبات أو الخطورة القصوى: حظر نهائي للحساب و/أو حظر الجهاز (IP).\n- الإدارة صاحبة القرار النهائي في تفسير الشروط وتطبيق العقوبات وتحديد مدّتها.\n\n" +
                        "6) العفو (طلب المراجعة):\n- من يكتشف خطأً برمجياً ويُبلغ عنه بصدق بدلاً من استغلاله يُعفى ويُكافأ.\n- اللاعب الذي يعترف طوعاً عن غش أو استغلال ويعيد كل ما حصل عليه بطرق غير مشروعة قبل اكتشافه يُعفى من العقوبة.\n- يحق لأي لاعب محظور تقديم طلب مراجعة/عفو واحد خلال 30 يوماً من تاريخ العقوبة ويُراجع بإنصاف.\n- إذا ثبت أن البلاغ كاذب أو أن العقوبة صدرت بخطأ تُرفع فوراً ويعود الحساب لحالته الأصلية.\n- الإدارة غير ملزمة بقبول الطلبات، وقرارها النهائي ملزم.\n\n" +
                        "7) أحكام عامة:\n- يجوز للإدارة تعديل هذه الشروط في أي وقت وسيتم إبلاغ اللاعبين.\n- الاستمرار في استخدام اللعبة بعد التعديل يعني موافقتك على الشروط الجديدة.\n- اللعبة مجانية، وقد تتعرض البيانات لأي خلل مؤقت دون مسؤولية الإدارة عن خسائر مؤقتة.\n- بموافقتك على لعب اللعبة فأنت تقر بقراءتك لهذه الشروط وفهمك لها.\n- إذا تعارض أي بند من هذه الشروط مع قانونك المحلي الذي لا يجوز الاتفاق على تجاوزه، يبقى البند ساري المفعول فيما لا يُخالف القانون ويُفسر بمعزل عن النص المخالف.\n\n" +
                        "8) الملكية الفكرية والأصول الرقمية:\n- جميع شعارات وأسماء ومحتوى هذه اللعبة ملك للإدارة وتُستخدم لأغراض التشغيل فقط.\n- الحسابات والعناصر والعملات داخل اللعبة رقمية وليست أموالاً حقيقية ولا يمكن تحويلها إلى عملة حقيقية، وتبقى ملكاً للعبة ما دام الحساب نشطاً.\n- يُحظر نسخ محتوى اللعبة أو إعادة توزيعه أو هندسة عكسيته أو استخدامه خارج اللعبة دون إذن كتابي مسبق.\n\n" +
                        "9) المسؤولية والحدود:\n- الإدارة تبذل جهدها للإبقاء على اللعبة متاحة وبياناتك سالمة، لكنها غير مسؤولة عن انقطاع مؤقت أو مدة حاصرتها أنظمة الاستضافة أو مشاكل شبكة خارجة عن إرادتها.\n- اللاعب مسؤول عن أجهزته وحسابه وأي تعاملات تتم من خلال الـ ID الخاص به.\n- القرارات الإدارية المتعلقة بتطبيق الشروط نهائية ما لم يوافق عليها بند العفو.\n\n" +
                        "10) الاتصال بنا:\n- لأي شكوى أو استفسار أو طلب مراجعة، أو للإبلاغ عن خطأ برمجي، تواصل معنا عبر مجموعة تيليجرام الرسمية داخل اللعبة.\n- نرد على جميع المراسلات خلال وقت معقول، ونفضّل أن تُرسل شكوى واحدة كاملة بمرفقاتها حتى لا يضيع طلبك.\n- آخر تحديث لهذه الشروط: 13 سبتمبر 2026.",
                "Terms of Use\n\n" +
                        "1) Account and ID:\n- The ID is the only key to your account. Sharing, selling or handing it to anyone is prohibited.\n- You are solely responsible for keeping your ID secret. The staff cannot recover an account lost because you shared your ID.\n- We are permitted to access or view devices' IP addresses for the purpose of banning cheating users and other game-protection purposes.\n\n" +
                        "2) In-Game Conduct:\n- Insulting, cursing or bullying in chat, arena names or tribes is prohibited.\n- Advertising outside links or accounts without staff permission is prohibited.\n- Posting malicious links or any offensive, political or religious content that provokes others is prohibited.\n- Impersonating staff, developers or other players is prohibited.\n- Spamming the chat or harassing players is prohibited.\n- Misusing the voice chat in arenas or sending offensive/annoying sounds is prohibited.\n\n" +
                        "3) Cheating and Exploits:\n- Using any software or modifications that give an unfair advantage is prohibited.\n- Exploiting bugs is forbidden. Anyone who finds a bug and exploits it instead of reporting it will be punished.\n- Creating multiple accounts to cheat or manipulate prizes is prohibited.\n- Selling or trading IDs, currencies or items for real money is prohibited.\n\n" +
                        "4) Reports:\n- Any report against a player must have a genuine and clear reason.\n- No report is accepted without conclusive evidence (a clear screenshot, an audio/video clip, or an in-game log proving it).\n- When a report meets the requirements, the accused player is placed under monitoring for between 24 hours and two weeks, and their logs are carefully reviewed before any decision.\n- To verify voice reports, supervisory staff may listen directly to the arena voice chat (invisible monitoring without revealing their presence) even while the players are there.\n- A false report made to harm another player or damage their reputation causes the reporter to be punished under the same penalty table.\n- Repeated reports without any evidence are considered harassment toward the game and the staff and are punishable.\n\n" +
                        "5) Penalty System (escalating):\n- First minor violation: a clear written warning.\n- Repeating: temporary mute or chat ban (from hours to days).\n- Medium violations: temporary account freeze (jail) from days to weeks depending on severity.\n- Cheating or exploiting bugs: confiscation of all illegitimate gains + temporary or permanent freeze.\n- Impersonation, fraud or selling accounts/IDs: permanent account ban.\n- Repeat offenses after penalties or extreme severity: permanent account ban and/or device (IP) ban.\n- The staff has the final say in interpreting the terms, applying penalties and setting their duration.\n\n" +
                        "6) Amnesty (Review Request):\n- Whoever genuinely finds a bug and reports it instead of exploiting it is pardoned and rewarded.\n- A player who voluntarily admits to cheating or exploiting and returns everything gained illegitimately before being caught is pardoned.\n- Any banned player may submit one review/amnesty request within 30 days of the penalty, and it will be reviewed fairly.\n- If a report is proven false or a penalty was issued by mistake, it is lifted immediately and the account is restored.\n- The staff is not obligated to accept requests, and its final decision is binding.\n\n" +
                        "7) General Provisions:\n- The staff may modify these terms at any time, and players will be notified.\n- Continuing to use the game after changes means you accept the new terms.\n- The game is free to play, and data may experience temporary issues without staff liability for temporary losses.\n- By playing the game you confirm that you have read and understood these terms.\n- If any clause of these terms conflicts with a local law that cannot be waived by agreement, that clause stays valid to the extent it does not violate the law and is interpreted apart from the conflicting text.\n\n" +
                        "8) Intellectual Property & Digital Assets:\n- All logos, names and content of this game belong to the staff and are used for operation only.\n- Accounts, items and in-game currencies are digital and are not real money and cannot be converted into real currency; they remain game property while the account is active.\n- Copying, redistributing, reverse-engineering or using game content outside the game without prior written permission is prohibited.\n\n" +
                        "9) Liability & Limits:\n- The staff makes its best effort to keep the game available and your data safe, but is not liable for temporary outages, hosting limitations or network issues beyond its control.\n- The player is responsible for their device, their account, and any transactions made through their ID.\n- Staff decisions about applying these terms are final unless covered by the amnesty clause.\n\n" +
                        "10) Contact Us:\n- For any complaint, question, review request, or to report a bug, contact us through the official in-game Telegram group.\n- We reply to all correspondence within a reasonable time; please send one complete complaint with its attachments so your request is not lost.\n- Last updated: September 13, 2026.");
    }

    public static String privacyText() {
        return GameApp.T(
                "سياسة الخصوصية\n\n" +
                        "1) البيانات التي نجمعها:\n- مخزن في حسابك: اسم اللاعب، المستوى، الإحصائيات، المعدات، الموارد، السجل.\n- نستخدم عنوان IP للجهاز لأغراض الحماية (منع الحظر/الغش) ولا ننشره أبداً.\n- يُسمح لنا بالوصول إلى عنوان IP الخاص بالأجهزة أو رؤيته لأغراض حظر المستخدمين الغشاشين وأغراض أخرى.\n- عند وجود بلاغ رسمي ضدك قد تُراجع وتُخزن مؤقتاً سجلات نشاطك (دردشة، مبارزات، تعاملات) لأغراض التحقيق.\n- لا نجمع أي بيانات شخصية حقيقية (اسم حقيقي، رقم هاتف، بريد إلكتروني) مطلقاً.\n\n" +
                        "2) طريقة التخزين:\n- تُحفظ بياناتك على خادم آمن مع نسخ احتياطية دورية.\n- لا نبيع ولا نشارك بياناتك مع أي طرف ثالث.\n- بياناتك تُستخدم فقط لتشغيل اللعبة وتحسين التجربة.\n\n" +
                        "3) بيانات محلية على جهازك:\n- نحفظ بعض الإعدادات محلياً (اللغة، الصوت، آخر معرف).\n- تبقى البيانات المحلية على جهازك ويمكن مسحها بمسح بيانات التطبيق.\n\n" +
                        "4) حقوقك:\n- لك الحق في حذف حسابك نهائياً من قائمة الإعدادات في أي وقت.\n- عند حذف الحساب تُمسح بياناتك من الخادم نهائياً (عدا السجلات الأمنية الضرورية).\n- لك الحق في معرفة نتيجة التحقيق إذا وقعت عليك عقوبة أو مراقبة.\n- لك الحق في التواصل معنا للاستفسار عن بياناتك.\n\n" +
                        "5) الأمان:\n- نستخدم إجراءات لحماية الحسابات من الوصول غير المصرح به.\n- لا تشارك الـ ID مع أحد؛ أي وصول عبر الـ ID يُحمل لك وحدك.\n\n" +
                        "6) المراقبة والتحقيق:\n- المراقبة (من 24 ساعة حتى أسبوعين) تُفعّل فقط عند وجود بلاغ مدعوم بدليل قاطع.\n- تشمل المراقبة الدردشة الصوتية العامة في الساحات؛ فقد يستمع عضو الإشراف إليها مباشرة للتحقق من البلاغات، وقد تتم المراقبة بشكل غير مرئي دون علم اللاعبين بوجود المراقب.\n- سجلات المراقبة تُستخدم حصرياً للتحقيق في المخالفات ولا تُباع ولا تُشارك مع أي طرف ثالث.\n- تُحذف سجلات المراقبة نهائياً بعد انتهاء التحقيق.\n\n" +
                        "7) مدة الاحتفاظ بالبيانات:\n- تُحتفظ ببيانات اللعب العادية طالما الحساب نشط، وتُحذف عند حذفك للحساب إلا ما تقتضيه السجلات الأمنية والمصادقة.\n- سجلات الـ IP تُستخدم مؤقتاً لأغراض الحماية ومطابقة الأجهزة، ولا تُعرض لأي لاعب ولا تُستخدم لأي غرض تسويقي.\n- سجلات البلاغات والتحقيقات تُحذف نهائياً بعد إغلاقها.\n\n" +
                        "8) الخدمات الخارجية والروابط:\n- قد تحتوي اللعبة على روابط لصفحات التواصل الاجتماعي الخاصة بنا (تيليجرام وغيرها)؛ أي استخدام لتلك الخدمات يخضع لسياسات الخصوصية الخاصة بها.\n- لا نتحمل المسؤولية عن أي محتوى أو سلوك في هذه الخدمات الخارجية.\n\n" +
                        "9) الأطفال والرقابة الأبوية:\n- اللعبة موجهة للجميع، ومن ينصح بإشراف الوالدين على التفاعل مع المجتمع والدردشة.\n- لا نجمع بيانات شخصية حقيقية عن المستخدمين القاصرين، وتطبق نفس سياسات الحماية على الجميع.\n\n" +
                        "10) التغييرات على هذه السياسة:\n- قد نحدث هذه السياسة من وقت لآخر لمواكبة تطور اللعبة أو المتطلبات القانونية.\n- عند حدوث تغيير جوهري سنعرض نسخة محدثة بتواريخ واضحة، واستمرارك في اللعب يعني موافقتك على النسخة المحدثة.\n\n" +
                        "11) الاتصال بنا (مسؤول البيانات):\n- لأي استفسار عن بياناتك أو تصحيحها أو حذفها، تواصل معنا عبر تيليجرام.\n- نرد على طلبات الخصوصية خلال وقت معقول وبدون تكلفة.\n- آخر تحديث لهذه السياسة: 13 سبتمبر 2026.",
                "Privacy Policy\n\n" +
                        "1) Data We Collect:\n- Stored in your account: player name, level, stats, gear, resources, history.\n- We use the device IP address for protection purposes (ban/anti-cheat) and never publish it.\n- We are permitted to access or view devices' IP addresses for the purpose of banning cheating users and other purposes.\n- When there is an official report against you, your activity logs (chat, duels, transactions) may be reviewed and temporarily stored for investigation purposes.\n- We never collect real personal data (real name, phone number, email address).\n\n" +
                        "2) How Data Is Stored:\n- Your data is kept on a secure server with periodic backups.\n- We never sell or share your data with any third party.\n- Your data is used only to run the game and improve the experience.\n\n" +
                        "3) Local Data on Your Device:\n- Some settings are saved locally (language, sound, last ID).\n- Local data stays on your device and can be erased by clearing the app data.\n\n" +
                        "4) Your Rights:\n- You may permanently delete your account from the Settings menu at any time.\n- When the account is deleted, your data is erased from the server permanently (except necessary security logs).\n- You have the right to know the outcome of an investigation if a penalty or monitoring was placed on you.\n- You may contact us to ask about your data.\n\n" +
                        "5) Security:\n- We use measures to protect accounts from unauthorized access.\n- Never share your ID; any access through the ID is your responsibility alone.\n\n" +
                        "6) Monitoring & Investigation:\n- Monitoring (from 24 hours up to two weeks) is only activated when there is a report backed by conclusive evidence.\n- Monitoring includes the public arena voice chat; a staff member may listen to it directly to verify reports, and the monitoring may be invisible so players never know a monitor is present.\n- Monitoring logs are used exclusively to investigate violations and are never sold or shared with any third party.\n- Monitoring logs are permanently deleted once the investigation ends.\n\n" +
                        "7) Data Retention:\n- Normal gameplay data is kept as long as the account is active and is deleted when you delete the account, except what is required by security and authentication records.\n- IP records are used temporarily for protection and device-matching purposes, are never shown to any player, and are never used for marketing.\n- Report and investigation records are permanently deleted once closed.\n\n" +
                        "8) External Services & Links:\n- The game may contain links to our social media pages (Telegram and others); any use of those services is governed by their own privacy policies.\n- We are not responsible for any content or behavior on those external services.\n\n" +
                        "9) Children & Parental Oversight:\n- The game is open to everyone, and we advise parental supervision over community and chat interaction.\n- We never collect real personal data from underage users, and the same protection policies apply to everyone.\n\n" +
                        "10) Changes to This Policy:\n- We may update this policy from time to time to keep pace with the game's evolution or legal requirements.\n- On any material change we will show an updated version with clear dates, and your continued use means you accept the updated version.\n\n" +
                        "11) Contact Us (Data Officer):\n- For any request about your data, to correct or delete it, contact us via Telegram.\n- We answer privacy requests within a reasonable time and free of charge.\n- Last updated: September 13, 2026.");
    }

    public static void openLegalUI() {
        final Context ctx = GameApp.uiCtx();
        final String[] opts = {
                GameApp.T("شروط الاستخدام", "Terms of Use"),
                GameApp.T("سياسة الخصوصية", "Privacy Policy")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("الشروط وسياسة الخصوصية", "Terms & Privacy Policy"));
        b.setItems(opts, (d, i) -> {
            String text = i == 0 ? termsText() : privacyText();
            String title = i == 0 ? GameApp.T("شروط الاستخدام", "Terms of Use") : GameApp.T("سياسة الخصوصية", "Privacy Policy");
            showDocPage(ctx, title, text);
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    /** Renders a long document as a clean, sectioned, professional-looking page
     *  (bold colored headings, spaced paragraphs, line-height, dividers) instead
     *  of one glued block of text. */
    private static void showDocPage(final Context ctx, final String title, final String text) {
        AlertDialog page = new AlertDialog.Builder(ctx)
                .setView(U.scroll(ctx, buildDocView(ctx, title, text)))
                .setCancelable(true)
                .create();
        if (U.uiReady()) page.show();
    }

    /** Opens one legal document with an explicit "I have read and agree" button. */
    public static void openLegalDoc(final boolean privacy, final Runnable onAgree) {
        final Context ctx = GameApp.uiCtx();
        if (ctx == null) return;
        final String title = privacy ? GameApp.T("سياسة الخصوصية", "Privacy Policy") : GameApp.T("شروط الاستخدام", "Terms of Use");
        final String text = privacy ? privacyText() : termsText();
        final String agreeLabel = privacy
                ? GameApp.T("أقر بأنني قرأت ووافقت على سياسة الخصوصية", "I have read and agree to the Privacy Policy")
                : GameApp.T("أقر بأنني قرأت ووافقت على شروط الاستخدام", "I have read and agree to the Terms of Use");
        AlertDialog page = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(U.scroll(ctx, buildDocView(ctx, title, text)))
                .setCancelable(false)
                .setPositiveButton(agreeLabel, (d, w) -> {
                    if (onAgree != null) onAgree.run();
                })
                .setNegativeButton(GameApp.T("رجوع", "Back"), null)
                .create();
        if (U.uiReady()) page.show();
    }

    private static LinearLayout buildDocView(final Context ctx, final String title, final String text) {
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(56, 36, 56, 40);

        TextView pageTitle = U.text(ctx, title, 21, U.GOLD, true);
        pageTitle.setGravity(android.view.Gravity.CENTER);
        lay.addView(pageTitle);

        View hsep = new View(ctx);
        hsep.setBackgroundColor(U.LIGHT_BLUE);
        lay.addView(hsep, U.lpMargins(LinearLayout.LayoutParams.MATCH_PARENT, 2, 0, 8, 0, 6));

        if (text != null) {
            String[] blocks = text.split("\n\n");
            for (String block : blocks) {
                if (block == null || block.trim().isEmpty()) continue;
                String[] lines = block.split("\n");
                if (lines == null || lines.length == 0) continue;
                String first = lines[0].trim();
                boolean isTitleOnly = lines.length == 1 && first.length() <= 60 && !first.endsWith(":");
                boolean isHeading = lines.length == 1 && !isTitleOnly && first.endsWith(":")
                        || lines.length > 1;
                if (isHeading) {
                    TextView h = U.text(ctx, first, 16, U.LIGHT_BLUE, true);
                    h.setPadding(0, 18, 0, 6);
                    lay.addView(h);
                } else if (!isTitleOnly) {
                    TextView h = U.text(ctx, first, 15, U.ORANGE, true);
                    h.setPadding(0, 14, 0, 4);
                    lay.addView(h);
                } else {
                    TextView h = U.text(ctx, first, 14, U.GOLD, true);
                    h.setGravity(android.view.Gravity.CENTER);
                    h.setPadding(0, 6, 0, 10);
                    lay.addView(h);
                }
                StringBuilder body = new StringBuilder();
                for (int li = isTitleOnly ? 1 : (isHeading ? 1 : 0); li < lines.length; li++) {
                    body.append(lines[li]).append('\n');
                }
                String bodyStr = body.toString().trim();
                if (!bodyStr.isEmpty()) {
                    TextView t = U.text(ctx, bodyStr, 13, U.WHITE, false);
                    t.setLineSpacing(0, 1.25f);
                    lay.addView(t);
                }
            }
        }

        TextView foot = U.text(ctx, GameApp.T("آخر تحديث: 13 سبتمبر 2026", "Last updated: September 13, 2026"), 11, U.GRAY, false);
        foot.setPadding(0, 22, 0, 0);
        lay.addView(foot);
        return lay;
    }

    private static void toggleMute() {
        GameApp.isMuted = !GameApp.isMuted;
        GameApp.prefs.edit().putBoolean("is_muted", GameApp.isMuted).apply();
        if (!GameApp.isMuted) {
            GameApp.sound.playSnd("click.mp3");
        }
    }

    private static void copyId() {
        final Context ctx = GameApp.uiCtx();
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("ID", GameApp.player.username));
            U.alert(ctx, null, GameApp.T("تم نسخ المعرف", "ID copied"), GameApp.T("حسنا", "OK"), null);
        }
    }

    private static void serverStatus() {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("فحص السيرفر", "Checking Server"), GameApp.T("جاري الاتصال بالسيرفر...", "Connecting to server..."), true);
        new Thread(() -> {
            Integer ms = ServerStatus.pingSync(5000);
            final boolean up;
            final String state;
            final String ping;
            if (ms != null) {
                up = true;
                state = GameApp.T("متصل", "Online");
                ping = ms + "ms";
            } else {
                Integer ms2 = ServerStatus.pingSync(30000);
                if (ms2 != null) {
                    up = true;
                    state = GameApp.T("كان نائماً ثم استيقظ", "Was asleep, now awake");
                    ping = ms2 + "ms";
                } else {
                    up = false;
                    state = GameApp.T("غير متصل", "Offline");
                    ping = "—";
                }
            }
            final boolean fUp = up;
            final String fState = state;
            final String fPing = ping;
            new Handler(Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("حالة السيرفر", "Server Status"));
                b.setView(U.msg(GameApp.T("الحالة:", "Status:") + fState
                        + GameApp.T("\nزمن الاستجابة:", "\nPing:") + fPing
                        + (fUp ? "" : GameApp.T("\n\nالسيرفر غير متصل حالياً. إذا كان نائماً فسوف يستيقظ تلقائياً عند أول طلب من اللعبة.", "\n\nThe server is currently offline. If it is asleep it will wake up automatically on the first request from the game."))));
                b.setPositiveButton(GameApp.T("إعادة الفحص", "Re-check"), (d, w) -> serverStatus());
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                b.show();
            });
        }).start();
    }

    private static void social() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final String[] opts = {
            GameApp.T("مجموعتنا على التليجرام", "Our Telegram Group"),
            GameApp.T("قناتنا على التليجرام", "Our Telegram Channel"),
            GameApp.T("قناة اليوتيوب", "YouTube Channel")
        };
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("تواصل معنا (مكافأة 100 ألف ذهب و50 كريستال و20 ألماس لمرة واحدة)", "Contact Us (one-time reward: 100,000 gold, 50 crystals, 20 diamonds)"));
        b.setItems(opts, (d, i) -> {
            final String url;
            if (i == 0) url = "https://t.me/ClashOfLegends2";
            else if (i == 1) url = "https://t.me/ObsidianVanguard";
            else url = "https://youtube.com/@obsidianvanguardtech?si=7Tk0R8q5hQNMZfu0";
            try {
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
            if (!p.socialRewardClaimed) {
                p.socialRewardClaimed = true;
                p.gold += 100000;
                p.crystals += 50;
                p.diamonds += 20;
                SaveSystem.saveAndRefresh();
                U.alert(ctx, GameApp.T("مكافأة الانضمام", "Join Reward"),
                        GameApp.T("شكراً لانضمامك! لقد حصلت على:\n- 100,000 ذهب\n- 50 كريستال\n- 20 ألماس", "Thank you for joining! You received:\n- 100,000 gold\n- 50 crystals\n- 20 diamonds"),
                        GameApp.T("رائع", "Great"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static List<JSONObject> loadLinkedAccounts() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(GameApp.prefs.getString("linked_accounts", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.add(o);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static void openAccountsManager() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final List<JSONObject> linked = loadLinkedAccounts();
        final String mainId = GameApp.prefs.getString("main_account_id", p.username);
        final String mainName = GameApp.prefs.getString("main_account_name", p.name);
        final List<String> names = new ArrayList<>();
        String curMarker = (p.username.equals(mainId)) ? GameApp.T("[رئيسي - حالي]", "[Main - Current]") : GameApp.T("[رئيسي]", "[Main]");
        names.add(GameApp.T("الحساب الرئيسي:", "Main Account:") + mainName + curMarker);
        for (JSONObject acc : linked) {
            String marker = acc.optString("id", "").equals(p.username) ? GameApp.T("[حالي]", "[Current]") : "";
            names.add(acc.optString("name", acc.optString("id", "?")) + marker);
        }
        names.add(GameApp.T("إنشاء حساب جديد", "Create New Account"));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إدارة الحسابات (", "Account Manager (") + (1 + linked.size()) + "/4)");
        b.setItems(names.toArray(new String[0]), (d, i) -> {
            if (i == names.size() - 1) {
                createSubAccount();
            } else if (i == 0) {
                if (p.username.equals(mainId)) {
                    U.alert(ctx, null, GameApp.T("أنت بالفعل في الحساب الرئيسي!", "You are already in the main account!"), GameApp.T("حسنا", "OK"), null);
                } else {
                    switchToAccount(mainId);
                }
            } else {
                final JSONObject acc = linked.get(i - 1);
                if (acc.optString("id", "").equals(p.username)) {
                    U.alert(ctx, null, GameApp.T("أنت بالفعل في هذا الحساب!", "You are already in this account!"), GameApp.T("حسنا", "OK"), null);
                } else {
                    switchToAccount(acc.optString("id", ""));
                }
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void switchToAccount(final String id) {
        final Context ctx = GameApp.uiCtx();
        GameApp.prefs.edit().putString("saved_id", id).apply();
        U.alert(ctx, null, GameApp.T("جاري التبديل...", "Switching..."), GameApp.T("حسنا", "OK"), v -> MainActivity.inst.recreate());
    }

    private static void createSubAccount() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        final List<JSONObject> linked = loadLinkedAccounts();
        if (linked.size() >= 3) {
            U.alert(ctx, null, GameApp.T("لا يمكنك إنشاء أكثر من 3 حسابات إضافية (4 حسابات إجمالاً مع الرئيسي)!", "You cannot create more than 3 extra accounts (4 total including the main one)!"), GameApp.T("حسنا", "OK"), null);
            return;
        }
        if (p.gold < 100000000 || p.crystals < 150 || p.diamonds < 65) {
            U.alert(ctx, GameApp.T("موارد غير كافية", "Not Enough Resources"),
                    GameApp.T("إنشاء حساب جديد يتطلب:\n- 100,000,000 ذهب (لديك:", "Creating a new account requires:\n- 100,000,000 gold (you have:") + NumberUtil.formatNumber(p.gold)
                            + GameApp.T(")\n- 150 كريستال (لديك:", ")\n- 150 crystals (you have:") + p.crystals + GameApp.T(")\n- 65 ألماس (لديك:", ")\n- 65 diamonds (you have:") + p.diamonds + ")",
                    GameApp.T("حسنا", "OK"), null);
            return;
        }
        final EditText e = U.edit(ctx, GameApp.T("اسم الحساب الجديد...", "New account name..."));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إنشاء حساب جديد", "Create New Account"));
        LinearLayout lay = U.linear(ctx, true);
        lay.addView(U.msg(GameApp.T("تكلفة الإنشاء:\n- 100,000,000 ذهب\n- 150 كريستال\n- 65 ألماس", "Creation cost:\n- 100,000,000 gold\n- 150 crystals\n- 65 diamonds")));
        lay.addView(e);
        b.setView(lay);
        b.setPositiveButton(GameApp.T("إنشاء", "Create"), (d, w) -> {
            final String nn = e.getText().toString().trim();
            if (nn.isEmpty()) {
                U.alert(ctx, null, GameApp.T("يرجى إدخال اسم الحساب!", "Please enter an account name!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            p.gold -= 100000000;
            p.crystals -= 150;
            p.diamonds -= 65;
            final String newId = "SubAcc_" + p.username + "_" + (System.currentTimeMillis() / 1000);
            final JSONObject data = new JSONObject();
            try {
                data.put("username", newId);
                data.put("name", nn);
                data.put("level", 1);
                data.put("exp", 0);
                data.put("points", 0);
                data.put("gold", 1000);
                data.put("crystals", 5);
                data.put("diamonds", 10);
                JSONObject st = new JSONObject();
                st.put("strength", 20).put("endurance", 10).put("agility", 5).put("luck", 5).put("hp", 300).put("max_hp", 300);
                data.put("stats", st);
                data.put("temp_stats", new JSONObject());
                data.put("location", "المنطقة الآمنة");
                data.put("clan", "لا يوجد");
                data.put("steps", 0);
                data.put("tower_floor", 1);
                data.put("inventory", new JSONArray());
                data.put("equipped", new JSONArray());
                data.put("friends", new JSONArray());
                data.put("parent_account", p.username);
            } catch (Exception ignored) {}
            final ProgressDialog progress = U.progress(ctx, GameApp.T("إنشاء", "Creating"), GameApp.T("جاري إنشاء الحساب...", "Creating account..."), true);
            new Thread(() -> {
                Db.put("players/" + Db.encode(newId), data);
                try {
                    JSONArray arr = new JSONArray();
                    for (JSONObject o : loadLinkedAccounts()) arr.put(o);
                    arr.put(new JSONObject().put("id", newId).put("name", nn));
                    GameApp.prefs.edit().putString("linked_accounts", arr.toString()).apply();
                } catch (Exception ignored) {}
                if (GameApp.prefs.getString("main_account_id", "").isEmpty()) {
                    GameApp.prefs.edit().putString("main_account_id", p.username).putString("main_account_name", p.name).apply();
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    progress.dismiss();
                    SaveSystem.saveAndRefresh();
                    U.alert(ctx, GameApp.T("تم الإنشاء!", "Created!"), GameApp.T("تم إنشاء الحساب [", "Account [") + nn + GameApp.T("] بنجاح! يمكنك التبديل إليه من قسم الحسابات.", "] created successfully! You can switch to it from the Accounts section."), GameApp.T("حسنا", "OK"), null);
                });
            }).start();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void logout() {
        final Context ctx = GameApp.uiCtx();
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("تأكيد تسجيل الخروج", "Confirm Logout"));
        b.setView(U.msg(GameApp.T("هل أنت متأكد أنك تريد تسجيل الخروج؟\nسيتم حفظ حسابك ويمكنك العودة إليه باستخدام الـ ID الخاص بك في أي وقت.", "Are you sure you want to log out?\nYour account will be saved and you can return to it anytime using your ID.")));
        b.setPositiveButton(GameApp.T("نعم، سجل خروجي", "Yes, Log Me Out"), (d, w) -> {
            SaveSystem.saveAndRefresh();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                GameApp.prefs.edit().putString("saved_id", "").apply();
                MainActivity.inst.recreate();
            }, 1500);
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void deleteAccount() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("تنبيه خطير جداً", "Very Serious Warning"));
        b.setView(U.msg(GameApp.T("سيتم مسح حسابك نهائياً! سيتم خروجك من القبيلة وحذفك من قوائم جميع الأصدقاء ومسح كل بياناتك. هل أنت متأكد؟", "Your account will be permanently wiped! You will leave your tribe, be removed from all friends' lists, and all your data will be erased. Are you sure?")));
        b.setPositiveButton(GameApp.T("نعم، احذف كل شيء", "Yes, Delete Everything"), (d, w) -> {
            final ProgressDialog progress = U.progress(ctx, GameApp.T("حذف شامل", "Full Delete"), GameApp.T("جاري تنظيف بياناتك من السيرفر...", "Cleaning your data from the server..."), true);
            new Thread(() -> {
                try {
                    if (p.clan != null && !p.clan.equals("لا يوجد")) {
                        JSONObject cov = Db.get("tribes_system/" + Db.encode(p.clan));
                        if (cov != null) {
                            if (cov.optString("leader", "").equals(p.name)) {
                                Db.delete("tribes_system/" + Db.encode(p.clan));
                            } else {
                                JSONObject members = cov.optJSONObject("members");
                                if (members != null) members.remove(p.username);
                                cov.put("members_count", Math.max(0, cov.optInt("members_count", 1) - 1));
                                Db.put("tribes_system/" + Db.encode(p.clan), cov);
                            }
                        }
                    }
                    for (FriendEntry f : p.friendsDetailed) {
                        try {
                            JSONObject t = Db.get("players/" + Db.encode(f.username));
                            if (t == null) continue;
                            boolean changed = false;
                            JSONObject patch = new JSONObject();
                            JSONArray fd = t.optJSONArray("friends_detailed");
                            if (fd != null) {
                                JSONArray nd = new JSONArray();
                                for (int i = 0; i < fd.length(); i++) {
                                    JSONObject e = fd.optJSONObject(i);
                                    if (e != null && e.optString("username", "").equals(p.username)) {
                                        changed = true;
                                        continue;
                                    }
                                    nd.put(e);
                                }
                                if (changed) { try { patch.put("friends_detailed", nd); } catch (Exception ignored) {} }
                            }
                            JSONArray fr = t.optJSONArray("friends");
                            boolean ch2 = false;
                            if (fr != null) {
                                JSONArray nf = new JSONArray();
                                for (int i = 0; i < fr.length(); i++) {
                                    if (p.name.equals(fr.optString(i))) {
                                        ch2 = true;
                                        continue;
                                    }
                                    nf.put(fr.optString(i));
                                }
                                if (ch2) { try { patch.put("friends", nf); } catch (Exception ignored) {} }
                            }
                            if (changed || ch2) Db.patch("players/" + Db.encode(f.username), patch);
                        } catch (Exception ignored) {}
                    }
                    Db.delete("players/" + Db.encode(p.username));
                } catch (Exception ignored) {}
                new Handler(Looper.getMainLooper()).post(() -> {
                    progress.dismiss();
                    GameApp.player = null;
                    try {
                        new File(GameApp.ctx.getFilesDir(), "player_db.json").delete();
                    } catch (Exception ignored) {}
                    GameApp.prefs.edit()
                            .remove("saved_id")
                            .remove("player_data")
                            .apply();
                    LoginScreen.skipAutoLogin = true;
                    MainActivity.inst.recreate();
                });
            }).start();
        });
        b.setNegativeButton(GameApp.T("تراجع", "Back"), null);
        b.show();
    }
}
