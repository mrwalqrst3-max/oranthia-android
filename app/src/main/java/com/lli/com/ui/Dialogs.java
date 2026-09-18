package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.lli.com.GameApp;
import com.lli.com.MainActivity;
import com.lli.com.core.Db;
import com.lli.com.core.ItemData;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.PlayerSystem;
import com.lli.com.core.Quest;
import com.lli.com.core.SaveSystem;
import com.lli.com.core.ServerTime;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Dialogs {

    private static final String[] ZONE_NAMES = {"العفاريت", "الاسود", "الشياطين", "التنانين", "بوابة الظلام", "عرش الفراغ", "المجرة", "جزيرة الأرواح", "مملكة السماء", "أعماق الهاوية"};
    private static final String[] ZONE_NAMES_EN = {"Goblins", "Lions", "Demons", "Dragons", "Dark Gate", "Throne of the Void", "Galaxy", "Spirit Island", "Sky Kingdom", "Abyss Depths"};

    public static String zoneName(String loc) {
        if (loc == null) return GameApp.T("", "");
        if (loc.equals("المنطقة الآمنة")) return GameApp.T("المنطقة الآمنة", "Safe Zone");
        for (int i = 0; i < ZONE_NAMES.length; i++) {
            if (ZONE_NAMES[i].equals(loc)) return GameApp.T(loc, ZONE_NAMES_EN[i]);
        }
        return GameApp.T(loc, loc);
    }

    private static boolean mysteryBoxBusy = false;

    private static final String[] AUCTION_NAMES_EN = {
            "Reinforced Wooden Sword", "Sturdy Leather Shield", "Minor Wisdom Scroll", "Crown of the Bejeweled Kings",
            "Sword of Eternal Light", "Golden Dragon Shield", "Legendary Ring of Fortitude", "Legendary Necklace of the Red Dragon",
            "Eternal Power Scroll"
    };

    private static final String[] AUCTION_DESCS_EN = {
            "A simple wooden sword enhanced with a bit of magic for beginners.",
            "A shield made from the hide of wild animals, offering basic protection.",
            "A scroll containing ancient wisdom that permanently increases your health.",
            "A crown forged from the gold of the first kings and studded with rare gems. It grants its wearer unbreakable prestige and a massive increase to both attack and defense.",
            "A legendary sword forged from star metal and bathed in sunlight for a thousand years. It doubles strike power and blinds enemies with its flash.",
            "A shield carved from the scales of a mythical golden dragon that lived for thousands of years. It absorbs lethal blows and turns damage into healing energy.",
            "A rare ring forged from the hardest mountain metals. It grants its wearer immense stamina and the ability to keep fighting.",
            "A necklace made from the heart of a raging red dragon. It grants vast life energy and mighty defense against the strongest blows.",
            "A secret scroll written by the first warrior in the universe, holding the secrets of absolute power."
    };

    private static final String[] AUCTION_EFFECTS_EN = {
            "+50 permanent attack", "+30 permanent defense", "Permanently grants +500 max health when read.",
            "+5000 permanent attack, +5000 permanent defense, +10000 royal prestige",
            "+15000 permanent attack, doubles the first strike's damage in every battle",
            "+20000 permanent defense, absorbs 15% of incoming damage and converts it to health",
            "+100000 permanent health, +5000 permanent defense",
            "+250000 permanent health, +15000 permanent defense",
            "Permanently grants +50000 attack, +50000 defense, +500000 max health when read."
    };

    public static void openPlayerNewsUI() {
        Context ctx = GameApp.uiCtx();
        List<String> news = GameApp.player.news;
        if (news == null || news.isEmpty()) news = new ArrayList<>();
        if (news.isEmpty()) news.add(GameApp.T("لا توجد أخبار حالياً.", "No news right now."));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("أخبار الشخصية", "Character News"));
        List<String> newsDisp = new ArrayList<>();
        for (String n : news) newsDisp.add(com.lli.com.core.PlayerSystem.displayNews(n));
        b.setItems(newsDisp.toArray(new String[0]), null);
        b.setPositiveButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    public static void openPointsUI() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        String[] opts = {GameApp.T("هجوم +5", "Attack +5"), GameApp.T("دفاع +3", "Defense +3"), GameApp.T("صحة +20", "Health +20"), GameApp.T("رشاقة +2", "Agility +2"), GameApp.T("حظ +2", "Luck +2")};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("نقاط التطوير:", "Upgrade Points:") + p.points);
        b.setItems(opts, (d, i) -> {
            if (p.points <= 0) {
                U.toast(GameApp.T("لا توجد نقاط!", "No points left!"));
                return;
            }
            String[] methods = {GameApp.T("تطوير يدوي (نقطة بنقطة)", "Manual upgrade (point by point)"), GameApp.T("إدخال عدد النقاط (لوحة المفاتيح)", "Enter number of points (keyboard)")};
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(GameApp.T("طريقة التطوير", "Upgrade Method"));
            b2.setItems(methods, (d2, i2) -> {
                if (i2 == 0) {
                    applyPoint(i, 1);
                    if (p.points > 0) openPointsUI();
                } else {
                    EditText ed = new EditText(ctx);
                    ed.setInputType(InputType.TYPE_CLASS_NUMBER);
                    ed.setHint(GameApp.T("عدد النقاط...", "Number of points..."));
                    ed.setTextColor(Color.WHITE);
                    AlertDialog.Builder b3 = new AlertDialog.Builder(ctx);
                    b3.setTitle(GameApp.T("كم نقطة تريد وضعها؟", "How many points do you want to spend?"));
                    b3.setView(ed);
                    b3.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (dd, w) -> {
                        try {
                            int num = Integer.parseInt(ed.getText().toString());
                            if (num > 0) {
                                applyPoint(i, num);
                                if (p.points > 0) openPointsUI();
                            }
                        } catch (Exception ignored) {}
                    });
                    b3.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    b3.show();
                }
            });
            b2.setPositiveButton(GameApp.T("إغلاق", "Close"), null);
            b2.show();
        });
        b.setPositiveButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void applyPoint(int idx, int count) {
        PlayerData p = GameApp.player;
        int c = Math.min(count, p.points);
        if (idx == 0) p.stats.strength += (5 * c);
        else if (idx == 1) p.stats.endurance += (3 * c);
        else if (idx == 2) { p.stats.maxHp += (20 * c); p.stats.hp = p.stats.maxHp; }
        else if (idx == 3) p.stats.agility += (2 * c);
        else if (idx == 4) p.stats.luck += (2 * c);
        p.points -= c;
        SaveSystem.saveAndRefresh();
    }

    public static void redistributePoints() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إعادة توزيع النقاط", "Redistribute Points"));
        b.setView(U.msg(GameApp.T("سيتم حذف تأثير نقاط التطوير فقط مع الحفاظ على معداتك.\nسيتم إرجاع جميع نقاط التطوير منذ بداية اللعبة.\nالتكلفة: 5 ألماس", "Only the effect of upgrade points will be removed while keeping your equipment.\nAll upgrade points since the start of the game will be returned.\nCost: 5 diamonds")));
        b.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (d, w) -> {
            int totalEarnedPoints = (p.level - 1) * 4;
            if (p.points >= totalEarnedPoints) {
                U.alert(ctx, null, GameApp.T("لم تستخدم أي نقاط تطوير بعد! لا حاجة لإعادة التوزيع.", "You haven't used any upgrade points yet! No need to redistribute."), GameApp.T("حسنا", "OK"), null);
                return;
            }
            if (p.diamonds >= 5) {
                p.diamonds -= 5;
                p.points = totalEarnedPoints;
                double str = 0, end = 0, hp = 0, agi = 0, lck = 0;
                if (p.equipped != null) {
                    for (String itemName : p.equipped) {
                        if (p.inventory != null) {
                            for (ItemData invItem : p.inventory) {
                                if (invItem.name.equals(itemName) && invItem.boosts != null) {
                                    str += invItem.boosts.str;
                                    end += invItem.boosts.end;
                                    hp += invItem.boosts.hp;
                                    agi += invItem.boosts.agi;
                                    lck += invItem.boosts.lck;
                                }
                            }
                        }
                    }
                }
                p.stats.strength = 20 + str;
                p.stats.endurance = 10 + end;
                p.stats.maxHp = 300 + hp;
                p.stats.agility = 5 + agi;
                p.stats.luck = 5 + lck;
                p.stats.hp = Math.max(p.stats.hp, p.stats.maxHp);
                SaveSystem.saveAndRefresh();
                U.alert(ctx, GameApp.T("تمت إعادة التوزيع", "Redistribution Done"),
                        GameApp.T("تم استرجاع", "Restored") + totalEarnedPoints + GameApp.T("نقطة تطوير.\nمباركات معداتك محفوظة بالكامل.\nقوتك الحالية تعتمد على ليفلك ومعداتك فقط.", "upgrade points.\nYour equipment boosts are fully preserved.\nYour current power depends only on your level and equipment."),
                        GameApp.T("موافق", "OK"), null);
            } else {
                U.alert(ctx, null, GameApp.T("لا تملك ألماس كافٍ!", "Not enough diamonds!"), GameApp.T("حسنا", "OK"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    public static void dailyGift() {
        Context ctx = GameApp.uiCtx();
        ServerTime.fetch((currentServerTime, ok) -> {
            if (!ok) {
                U.alert(ctx, GameApp.T("خطأ في الاتصال", "Connection Error"), GameApp.T("فشل التحقق من وقت السيرفر. تأكد من اتصالك بالإنترنت وحاول مجدداً.", "Failed to verify the server time. Check your internet connection and try again."), GameApp.T("حسنا", "OK"), null);
                return;
            }
            PlayerData p = GameApp.player;
            long d = currentServerTime - p.lastGiftTime;
            if (d >= 86400) {
                if (d > 172800) p.loginStreak = 1; else p.loginStreak += 1;
                p.lastGiftTime = currentServerTime;
                int streakMult = Math.min(p.loginStreak, 7);
                long interest = (long) Math.floor(p.bankGold * 0.08);
                p.bankGold += interest;
                int r = NumberUtil.rand(1, 100);
                p.diamonds += 3;
                String msg = GameApp.T("أرباح البنك (8%): +", "Bank profit (8%): +") + interest + GameApp.T("ذهب\n\nهدية اليوم (يوم", "gold\n\nToday's gift (day") + p.loginStreak + GameApp.T("):\n+ 3 ألماس [مكافأة ثابتة]\n", "):\n+ 3 diamonds [fixed bonus]\n");
                int dailyLuckBonus = (int) Math.floor((p.stats.luck) * 20);
                if (r <= 20) {
                    long g = (3000 + dailyLuckBonus) * streakMult;
                    long e = 1000L * streakMult;
                    int pt = 2;
                    p.gold += g;
                    PlayerSystem.addExp(e);
                    p.points += pt;
                    msg = msg + "+" + g + GameApp.T("ذهب\n+", "gold\n+") + e + GameApp.T("خبرة\n+", "exp\n+") + pt + GameApp.T("نقاط تطوير", "upgrade points");
                } else if (r <= 40) {
                    long g = (3000 + dailyLuckBonus) * streakMult;
                    long e = 1000L * streakMult;
                    long c = 3;
                    p.gold += g;
                    PlayerSystem.addExp(e);
                    p.crystals += c;
                    msg = msg + "+" + g + GameApp.T("ذهب\n+", "gold\n+") + e + GameApp.T("خبرة\n+", "exp\n+") + c + GameApp.T("كريستال", "crystals");
                } else if (r <= 55) {
                    long c = 5;
                    long e = 2000L * streakMult;
                    p.crystals += c;
                    PlayerSystem.addExp(e);
                    msg = msg + "+" + c + GameApp.T("كريستال\n+", "crystals\n+") + e + GameApp.T("خبرة", "exp");
                } else if (r <= 70) {
                    long g = (5000 + dailyLuckBonus) * streakMult;
                    int pt = 3;
                    p.gold += g;
                    p.points += pt;
                    msg = msg + "+" + g + GameApp.T("ذهب\n+", "gold\n+") + pt + GameApp.T("نقاط تطوير", "upgrade points");
                } else if (r <= 85) {
                    long c = 10;
                    p.crystals += c;
                    msg = msg + GameApp.T("جائزة نادرة!\n+", "Rare reward!\n+") + c + GameApp.T("بلورات", "crystals");
                } else {
                    int pt = 5;
                    p.points += pt;
                    msg = msg + GameApp.T("جائزة نادرة!\n+", "Rare reward!\n+") + pt + GameApp.T("نقاط تطوير صافي", "net upgrade points");
                }
                final String fmsg = msg;
                if (!p.clan.equals("لا يوجد")) {
                    final long[] bonus = {0, 0};
                    new Thread(() -> {
                        try {
                            JSONObject dt = Db.get("tribes_system/" + Db.encode(p.clan));
                            if (dt != null) {
                                bonus[0] = dt.optLong("gold_mine_lv", 0) * 5000;
                                bonus[1] = dt.optLong("training_camp_lv", 0) * 3000;
                            }
                        } catch (Exception ignored) {}
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (bonus[0] > 0 || bonus[1] > 0) {
                                p.gold += bonus[0];
                                PlayerSystem.addExp(bonus[1]);
                            }
                            SaveSystem.saveAndRefresh();
                            GameApp.sound.playSnd("reward.mp3");
                            String bMsg = fmsg;
                            if (bonus[0] > 0 || bonus[1] > 0) {
                                bMsg += GameApp.T("\n\n[أرباح ميزات القبيلة]\n+", "\n\n[Tribe feature earnings]\n+") + bonus[0] + GameApp.T("ذهب\n+", "gold\n+") + bonus[1] + GameApp.T("خبرة", "exp");
                            }
                            final String fbMsg = bMsg;
                            U.alert(ctx, GameApp.T("المكافأة اليومية", "Daily Reward"), fbMsg, GameApp.T("استلام", "Claim"), null);
                        });
                    }).start();
                } else {
                    SaveSystem.saveAndRefresh();
                    GameApp.sound.playSnd("reward.mp3");
                    U.alert(ctx, GameApp.T("المكافأة اليومية", "Daily Reward"), fmsg, GameApp.T("استلام", "Claim"), null);
                }
            } else {
                U.alert(ctx, null, GameApp.T("استلمت هديتك اليوم! عد غداً.", "You already claimed today's gift! Come back tomorrow."), GameApp.T("حسنا", "OK"), null);
            }
        });
    }

    public static void meditation() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        if (p.meditationTime > 0) {
            long passed = ServerTime.now() - p.meditationTime;
            long gGain = (long) Math.floor(passed * 0.5);
            long eGain = (long) Math.floor(passed * 0.2);
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("استيقاظ", "Wake Up"));
            b.setView(U.msg(GameApp.T("جمعت لك اللعبة وأنت بالخارج:\n+", "The game collected for you while you were away:\n+") + gGain + GameApp.T("ذهب\n+", "gold\n+") + eGain + GameApp.T("خبرة\n\nهل تريد مضاعفة هذه الأرباح مقابل 2 ألماس؟", "exp\n\nDo you want to double these earnings for 2 diamonds?")));
            b.setPositiveButton(GameApp.T("مضاعفة (2 ألماس)", "Double (2 diamonds)"), (d, w) -> {
                if (p.diamonds >= 2) {
                    p.diamonds -= 2;
                    p.gold += (gGain * 2);
                    PlayerSystem.addExp(eGain * 2);
                    p.meditationTime = 0;
                    SaveSystem.saveAndRefresh();
                } else {
                    U.toast(GameApp.T("لا تملك ألماس كافٍ!", "Not enough diamonds!"));
                }
            });
            b.setNegativeButton(GameApp.T("استلام عادي", "Claim Normally"), (d, w) -> {
                p.gold += gGain;
                PlayerSystem.addExp(eGain);
                p.meditationTime = 0;
                SaveSystem.saveAndRefresh();
            });
            b.show();
        } else {
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("ساحة التأمل", "Meditation Square"));
            b.setView(U.msg(GameApp.T("سيقوم بطلك بجمع الذهب والخبرة حتى وأنت تغلق اللعبة!", "Your hero will collect gold and exp even while the game is closed!")));
            b.setPositiveButton(GameApp.T("بدء التأمل", "Start Meditating"), (d, w) -> {
                p.meditationTime = ServerTime.now();
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("بدأ التأمل... يمكنك الخروج الآن!", "Meditation started... you can exit now!"), GameApp.T("حسنا", "OK"), null);
            });
            b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b.show();
        }
    }

    public static void diamondShop() {
        Context ctx = GameApp.uiCtx();
        String[] opts = {
                GameApp.T("تعزيز القوة (5 ألماس): +50 هجوم، +25 دفاع دائم", "Power Boost (5 diamonds): +50 attack, +25 permanent defense"),
                GameApp.T("تجديد الطاقة (3 ألماس): استعادة الصحة كاملة", "Energy Refill (3 diamonds): Restore full health"),
                GameApp.T("عين الصقر (5 ألماس): +10 حظ دائم", "Hawk Eye (5 diamonds): +10 permanent luck"),
                GameApp.T("الارتقاء السريع (30 ألماس): لفل أب فوري", "Quick Level Up (30 diamonds): Instant level up")
        };
        String[] types = {"power", "full_heal", "luck_boost", "instant_level"};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("متجر الألماس الأسطوري", "Legendary Diamond Shop"));
        b.setItems(opts, (d, i) -> PlayerSystem.useDiamond(types[i]));
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    public static void auction() {
        Context ctx = GameApp.uiCtx();
        final Object[][] items = {
                {"سيف خشبي معزز", 1000000L, 50, 0, 0, "سلاح", "سيوف", "سيف خشبي بسيط لكنه معزز ببعض السحر للمبتدئين.", "+50 هجوم دائم", false},
                {"درع جلدي متين", 800000L, 0, 30, 0, "دروع", "درع خفيف", "درع مصنوع من جلد الحيوانات البرية، يوفر حماية أساسية.", "+30 دفاع دائم", false},
                {"مخطوطة الحكمة الصغيرة", 2000000L, 0, 0, 500, "مخطوطات", "", "مخطوطة تحتوي على نصائح طبية تزيد من صحتك الدائمة.", "تمنح +500 صحة قصوى بشكل دائم عند قراءتها.", true},
                {"تاج الملوك المرصع", 50000000000L, 5000, 5000, 0, "دروع", "خوذة", "تاج مصنوع من ذهب الملوك الأوائل ومرصع بجواهر نادرة. يمنح حامله هيبة لا تُقهر وزيادة ضخمة في الهجوم والدفاع معاً.", "+5000 هجوم دائم، +5000 دفاع دائم، +10000 هيبة ملكية", false},
                {"سيف النور الخالد", 150000000000L, 15000, 0, 0, "سلاح", "سيوف", "سيف أسطوري صُنع من معدن النجوم وغُمس في نور الشمس لألف عام. يُضاعف قوة الضربة ويُعمي الأعداء بوميضه.", "+15000 هجوم دائم، يُضاعف ضرر الضربة الأولى في كل معركة", false},
                {"درع التنين الذهبي", 300000000000L, 0, 20000, 0, "دروع", "درع ثقيل", "درع منحوت من قشور تنين ذهبي خرافي عمره آلاف السنين. يمتص الضربات القاتلة ويحول الضرر إلى طاقة شافية.", "+20000 دفاع دائم، يمتص 15% من الضرر الوارد ويحوله لصحة", false},
                {"خاتم الصمود الأسطوري", 800000000000L, 0, 0, 100000, "أدوات مساعدة", "خواتم", "خاتم نادر صُنع من أصلب المعادن الجبلية. يمنح حامله قوة تحمل هائلة وقدرة على الاستمرار في القتال.", "+100000 صحة دائم، +5000 دفاع دائم", false},
                {"قلادة التنين الأحمر الأسطورية", 1500000000000L, 0, 15000, 250000, "أدوات مساعدة", "قلادات", "قلادة صنعت من قلب تنين أحمر ثائر. تمنح طاقة حياة هائلة ودفاعاً جباراً ضد أقوى الضربات.", "+250000 صحة دائم، +15000 دفاع دائم", false},
                {"مخطوطة القوة الأزلية", 5000000000000L, 50000, 50000, 500000, "مخطوطات", "", "مخطوطة سرية كتبها أول محارب في الكون وتحتوي على أسرار القوة المطلقة.", "تمنح +50000 هجوم، +50000 دفاع، +500000 صحة قصوى بشكل دائم عند قراءتها.", true}
        };
        PlayerData p = GameApp.player;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("المزاد الملكي الأسطوري", "Legendary Royal Auction"));
        b.setView(U.msg(GameApp.T("مرحباً بك يا صاحب السمو في المزاد الملكي.\n\n رصيدك الحالي:", "Welcome, Your Highness, to the royal auction.\n\n Your current balance:") + NumberUtil.formatNumber(p.gold) + GameApp.T("ذهب", "gold")));
        b.setPositiveButton(GameApp.T("فتح قائمة المزايدة", "Open Bidding List"), (d, w) -> {
            String[] itemNames = new String[items.length];
            for (int i = 0; i < items.length; i++) {
                itemNames[i] = GameApp.T((String) items[i][0], AUCTION_NAMES_EN[i]) + "[" + NumberUtil.formatNumber(((Number) items[i][1]).longValue()) + GameApp.T("ذهب]", "gold]");
            }
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(GameApp.T("اختر قطعة للمزايدة", "Choose an item to bid on"));
            b2.setItems(itemNames, (d2, i) -> startBidding(ctx, items[i], i));
            b2.setNegativeButton(GameApp.T("رجوع", "Back"), null);
            b2.show();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void startBidding(Context ctx, final Object[] it, final int idx) {
        final String name = GameApp.T((String) it[0], AUCTION_NAMES_EN[idx]);
        final long basePrice = ((Number) it[1]).longValue();
        final long atk = ((Number) it[2]).longValue();
        final long def = ((Number) it[3]).longValue();
        final long hp = ((Number) it[4]).longValue();
        final String category = (String) it[5];
        final String subType = (String) it[6];
        final String desc = GameApp.T((String) it[7], AUCTION_DESCS_EN[idx]);
        final String effect = GameApp.T((String) it[8], AUCTION_EFFECTS_EN[idx]);
        final boolean isScroll = (Boolean) it[9];
        PlayerData p = GameApp.player;

        String infoMsg = GameApp.T("الوصف:\n", "\nDescription:\n") + desc + GameApp.T("\n\n التأثير:\n", "\n\nEffect:\n") + effect +
                GameApp.T("\n\n الإحصائيات:\n- هجوم: +", "\n\nStats:\n- Attack: +") + atk + GameApp.T("\n- دفاع: +", "\n- Defense: +") + def + GameApp.T("\n- صحة: +", "\n- Health: +") + hp +
                GameApp.T("\n\n سعر البداية:", "\n\n Starting price:") + NumberUtil.formatNumber(basePrice) + GameApp.T("ذهب", "gold");
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("" + name);
        b.setView(U.msg(infoMsg));
        b.setPositiveButton(GameApp.T("بدء المزايدة", "Start Bidding"), (d, w) -> {
            final long[] currentBid = {basePrice};
            final int[] botBidCount = {0};
            final int maxBotBids = NumberUtil.rand(3, 7);
            final String[] botNames = {GameApp.T("التاجر الملكي", "Royal Merchant"), GameApp.T("الأمير المجهول", "Mysterious Prince"), GameApp.T("جامع التحف", "Collector of Antiques"), GameApp.T("الكونت المظلم", "Dark Count")};
            final String currentBotName = botNames[NumberUtil.rand(0, botNames.length - 1)];
            final Handler handler = new Handler(Looper.getMainLooper());
            final Runnable[] showBid = new Runnable[1];

            showBid[0] = new Runnable() {
                @Override
                public void run() {
                    if (botBidCount[0] >= maxBotBids) {
                        AlertDialog.Builder win = new AlertDialog.Builder(ctx);
                        win.setTitle(GameApp.T("فزت بالمزاد!", "You won the auction!"));
                        win.setView(U.msg(GameApp.T("انسحب المنافسون! أنت الفائز بـ [", "The competitors withdrew! You won [") + name + GameApp.T("] بسعر", "] for") + NumberUtil.formatNumber(currentBid[0]) + GameApp.T("ذهب!", "gold!")));
                        win.setPositiveButton(GameApp.T("استلام القطعة", "Claim Item"), (dd, ww) -> {
                            if (p.gold >= currentBid[0]) {
                                p.gold -= currentBid[0];
                                ItemData ni = new ItemData();
                                ni.name = name;
                                ni.desc = desc;
                                ni.effect = effect;
                                ni.stats = new com.lli.com.core.Stats();
                                ni.category = category.isEmpty() ? (isScroll ? "مخطوطات" : "أخرى") : category;
                                ni.subType = subType;
                                ni.isScroll = isScroll;
                                ni.level = 1;
                                ni.boosts.str = atk;
                                ni.boosts.end = def;
                                ni.boosts.hp = hp;
                                p.inventory.add(ni);
                                SaveSystem.saveAndRefresh();
                                U.alert(ctx, GameApp.T("تم!", "Done!"), GameApp.T("تم إضافة [", "Added [") + name + GameApp.T("] إلى حقيبتك في فئة [", "] to your bag under category [") + ni.category + GameApp.T("]!", "]!"), GameApp.T("رائع!", "Awesome!"), null);
                            } else {
                                U.alert(ctx, GameApp.T("رصيد غير كافٍ", "Insufficient Balance"), GameApp.T("ليس لديك ذهب كافٍ!", "You don't have enough gold!"), GameApp.T("حسناً", "OK"), null);
                            }
                        });
                        win.setNegativeButton(GameApp.T("تراجع", "Retreat"), null);
                        win.show();
                        return;
                    }
                    String bidMsg = GameApp.T("المزايدة الحالية على:", "Current bid on:") + name +
                            GameApp.T("\n\n أعلى مزايدة الآن:", "\n\n Highest bid now:") + NumberUtil.formatNumber(currentBid[0]) + GameApp.T("ذهب", "gold") +
                            GameApp.T("\n آخر مزايد:", "\n Last bidder:") + currentBotName +
                            GameApp.T("\n\n رصيدك:", "\n\n Your balance:") + NumberUtil.formatNumber(p.gold) + GameApp.T("ذهب", "gold");
                    AlertDialog.Builder bd = new AlertDialog.Builder(ctx);
                    bd.setTitle(GameApp.T("المزايدة الحية", "Live Bidding"));
                    bd.setView(U.msg(bidMsg));
                    bd.setPositiveButton(GameApp.T("زايد أنت (+10%)", "Bid Yourself (+10%)"), (ddd, www) -> {
                        long playerNewBid = (long) Math.floor(currentBid[0] * 1.10);
                        if (p.gold < playerNewBid) {
                            U.alert(ctx, GameApp.T("رصيد غير كافٍ", "Insufficient Balance"), GameApp.T("تحتاج", "You need") + NumberUtil.formatNumber(playerNewBid) + GameApp.T("ذهب!", "gold!"), GameApp.T("حسناً", "OK"), null);
                            return;
                        }
                        currentBid[0] = playerNewBid;
                        ProgressDialog pd = U.progress(ctx, GameApp.T("المزايدة", "Auction"), currentBotName + GameApp.T("يزايد...", "is bidding..."), true);
                        handler.postDelayed(() -> {
                            pd.dismiss();
                            botBidCount[0] += 1;
                            currentBid[0] = (long) Math.floor(currentBid[0] * 1.08);
                            showBid[0].run();
                        }, NumberUtil.rand(1000, 2000));
                    });
                    bd.setNegativeButton(GameApp.T("الانسحاب", "Withdraw"), null);
                    bd.show();
                }
            };
            showBid[0].run();
        });
        b.setNegativeButton(GameApp.T("رجوع", "Back"), null);
        b.show();
    }

    public static void bank() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        boolean[] vaultOpen = {false};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("البنك الملكي", "Royal Bank"));
        b.setView(U.msg(GameApp.T("أهلاً بك في البنك الملكي! هنا يمكنك حفظ ذهبك بعيداً عن اللصوص، وستحصل على أرباح يومية قدرها 8% على إجمالي مدخراتك عند استلام الهدية اليومية!", "Welcome to the Royal Bank! Here you can keep your gold safe from thieves, and you will earn daily profits of 8% on your total savings when you claim the daily gift!")));
        b.setPositiveButton(GameApp.T("دخول", "Enter"), (d, w) -> {
            vaultOpen[0] = true;
            String[] opts = {GameApp.T("إيداع ذهب", "Deposit Gold"), GameApp.T("سحب ذهب", "Withdraw Gold")};
            AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
            b2.setTitle(GameApp.T("خزنتك (", "Your vault (") + (long) Math.floor(p.bankGold) + ")");
            b2.setItems(opts, (d2, i) -> {
                EditText ed = new EditText(ctx);
                ed.setInputType(InputType.TYPE_CLASS_NUMBER);
                ed.setHint(GameApp.T("المبلغ...", "Amount..."));
                ed.setTextColor(Color.WHITE);
                AlertDialog.Builder b3 = new AlertDialog.Builder(ctx);
                b3.setTitle(i == 0 ? GameApp.T("إيداع", "Deposit") : GameApp.T("سحب", "Withdraw"));
                b3.setView(ed);
                b3.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (dd, ww) -> {
                    try {
                        long a = Long.parseLong(ed.getText().toString().trim());
                        if (i == 0 && a > 0 && a <= p.gold) {
                            p.gold -= a;
                            p.bankGold += a;
                            SaveSystem.saveAndRefresh();
                            U.alert(ctx, null, GameApp.T("تم إيداع", "Deposited") + a + GameApp.T("ذهب بنجاح!", "gold successfully!"), GameApp.T("حسنا", "OK"), null);
                        } else if (i == 1 && a > 0 && a <= p.bankGold) {
                            p.bankGold -= a;
                            p.gold += a;
                            SaveSystem.saveAndRefresh();
                            U.alert(ctx, null, GameApp.T("تم سحب", "Withdrew") + a + GameApp.T("ذهب بنجاح!", "gold successfully!"), GameApp.T("حسنا", "OK"), null);
                        } else {
                            U.alert(ctx, null, GameApp.T("رقم خاطئ أو ذهب غير كافٍ!", "Wrong number or not enough gold!"), GameApp.T("حسنا", "OK"), null);
                        }
                    } catch (Exception ex) {
                        U.alert(ctx, null, GameApp.T("رقم خاطئ أو ذهب غير كافٍ!", "Wrong number or not enough gold!"), GameApp.T("حسنا", "OK"), null);
                    }
                });
                b3.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b3.show();
            });
            b2.setPositiveButton(GameApp.T("خروج", "Exit"), null);
            final AlertDialog vaultD = b2.show();
            vaultD.setOnDismissListener(dd -> {
                vaultOpen[0] = false;
                GameApp.sound.setAmbient("amb_city.mp3");
            });
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        GameApp.sound.setAmbient("amb_bank.mp3");
        AlertDialog bankD = b.show();
        bankD.setOnDismissListener(d -> {
            if (!vaultOpen[0]) GameApp.sound.setAmbient("amb_city.mp3");
        });
    }

    public static void energySpring() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("ينبوع الطاقة", "Energy Spring"));
        b.setView(U.msg(GameApp.T("استخدم 15 كريستال لزيادة دائمة عشوائية!", "Use 15 crystals for a random permanent boost!")));
        b.setPositiveButton(GameApp.T("استخدام", "Use"), (d, w) -> {
            if (p.crystals >= 15) {
                p.crystals -= 15;
                int roll = NumberUtil.rand(1, 4);
                String sn = "";
                if (roll == 1) { p.stats.strength += 10; sn = GameApp.T("الهجوم", "Attack"); }
                else if (roll == 2) { p.stats.endurance += 10; sn = GameApp.T("الدفاع", "Defense"); }
                else if (roll == 3) { p.stats.luck += 5; sn = GameApp.T("الحظ", "Luck"); }
                else if (roll == 4) { p.stats.maxHp += 50; sn = GameApp.T("الصحة", "Health"); }
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("حصلت على زيادة في [", "You got a boost in [") + sn + GameApp.T("]!", "]!"), GameApp.T("عظيم", "Great"), null);
            } else {
                U.alert(ctx, null, GameApp.T("كريستال غير كافٍ! تحتاج 15 كريستال.", "Not enough crystals! You need 15 crystals."), GameApp.T("رجوع", "Back"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    public static void luckWheel() {
        Context ctx = GameApp.uiCtx();
        ServerTime.fetch((curDay, ok) -> {
            if (!ok) {
                U.alert(ctx, GameApp.T("خطأ في الاتصال", "Connection Error"), GameApp.T("فشل التحقق من وقت السيرفر. تأكد من اتصالك بالإنترنت.", "Failed to verify the server time. Check your internet connection."), GameApp.T("حسنا", "OK"), null);
                return;
            }
            checkDailyReset();
            PlayerData p = GameApp.player;
            if (p.dailyWheelCount < 3) {
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("عجلة الحظ", "Wheel of Fortune"));
                b.setView(U.msg(GameApp.T("يمكنك اللعب مجاناً بالذهب (100 ذهب) لـ 3 مرات يومياً.\nلعبت اليوم:", "You can play with gold (100 gold) for free 3 times daily.\nPlayed today:") + p.dailyWheelCount + "/3"));
                b.setPositiveButton(GameApp.T("لعب (100 ذهب)", "Play (100 gold)"), (d, w) -> spinWheel("gold", 100));
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            } else if (p.dailyWheelCount < 6) {
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("عجلة الحظ", "Wheel of Fortune"));
                b.setView(U.msg(GameApp.T("انتهت المرات المجانية! يمكنك اللعب 3 مرات إضافية مقابل 10 كريستال للمرة.\nلعبت الإضافي:", "Free turns are over! You can play 3 extra times for 10 crystals each.\nExtra played:") + (p.dailyWheelCount - 3) + "/3"));
                b.setPositiveButton(GameApp.T("لعب (100 ذهب + 10 كريستال)", "Play (100 gold + 10 crystals)"), (d, w) -> spinWheel("crystal", 10));
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            } else {
                U.alert(ctx, null, GameApp.T("انتهت جميع محاولاتك لليوم! عد غداً.", "All your attempts for today are over! Come back tomorrow."), GameApp.T("حسنا", "OK"), null);
            }
        });
    }

    private static void spinWheel(String costType, long costVal) {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        if ((costType.equals("gold") && p.gold >= costVal) || (costType.equals("crystal") && p.crystals >= costVal)) {
            if (costType.equals("gold")) p.gold -= costVal; else p.crystals -= costVal;
            p.dailyWheelCount += 1;
            int r = NumberUtil.rand(1, 100);
            String msg;
            int luckBonus = (int) Math.floor(p.stats.luck * 10);
            if (r <= 60) {
                msg = GameApp.T("لم تفز بشيء هذه المرة.", "You won nothing this time.");
            } else if (r <= 85) {
                long goldWin = 1500 + luckBonus;
                p.gold += goldWin;
                msg = GameApp.T("كسبت", "You won") + goldWin + GameApp.T("ذهب!", "gold!");
                GameApp.sound.playSnd("reward.mp3");
            } else if (r <= 95) {
                PlayerSystem.addExp(3000);
                msg = GameApp.T("كسبت 3000 خبرة!", "You earned 3000 exp!");
                GameApp.sound.playSnd("reward.mp3");
            } else if (r <= 97) {
                p.crystals += 5;
                msg = GameApp.T("كسبت 5 كريستال!", "You earned 5 crystals!");
                GameApp.sound.playSnd("reward.mp3");
            } else {
                p.points += 3;
                msg = GameApp.T("الجائزة الكبرى! 3 نقاط تطوير!", "Grand prize! 3 upgrade points!");
                GameApp.sound.playSnd("reward.mp3");
            }
            SaveSystem.saveAndRefresh();
            U.alert(ctx, GameApp.T("عجلة الحظ", "Wheel of Fortune"), msg, GameApp.T("حسنا", "OK"), null);
        } else {
            U.alert(ctx, null, GameApp.T("الموارد غير كافية!", "Not enough resources!"), GameApp.T("حسنا", "OK"), null);
        }
    }

    public static void mysteryBox() {
        Context ctx = GameApp.uiCtx();
        ServerTime.fetch((curDay, ok) -> {
            if (!ok) {
                U.alert(ctx, GameApp.T("خطأ في الاتصال", "Connection Error"), GameApp.T("فشل التحقق من وقت السيرفر. تأكد من اتصالك بالإنترنت.", "Failed to verify the server time. Check your internet connection."), GameApp.T("حسنا", "OK"), null);
                return;
            }
            checkDailyReset();
            PlayerData p = GameApp.player;
            if (p.dailyMysteryCount < 3) {
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("صندوق الغموض", "Mystery Box"));
                b.setView(U.msg(GameApp.T("يمكنك فتح الصندوق بالذهب (1000 ذهب) لـ 3 مرات يومياً.\nفتحت اليوم:", "You can open the box with gold (1000 gold) 3 times daily.\nOpened today:") + p.dailyMysteryCount + "/3"));
                b.setPositiveButton(GameApp.T("فتح (1000 ذهب)", "Open (1000 gold)"), (d, w) -> openBox("gold", 1000));
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            } else if (p.dailyMysteryCount < 6) {
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("صندوق الغموض", "Mystery Box"));
                b.setView(U.msg(GameApp.T("انتهت المرات العادية! يمكنك الفتح 3 مرات إضافية مقابل 20 كريستال للمرة.\nفتحت الإضافي:", "Normal turns are over! You can open 3 extra times for 20 crystals each.\nExtra opened:") + (p.dailyMysteryCount - 3) + "/3"));
                b.setPositiveButton(GameApp.T("فتح (1000 ذهب + 20 كريستال)", "Open (1000 gold + 20 crystals)"), (d, w) -> openBox("crystal", 20));
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            } else {
                U.alert(ctx, null, GameApp.T("انتهت جميع محاولاتك لليوم! عد غداً.", "All your attempts for today are over! Come back tomorrow."), GameApp.T("حسنا", "OK"), null);
            }
        });
    }

    private static void openBox(String costType, long costVal) {
        if (mysteryBoxBusy) return;
        mysteryBoxBusy = true;
        try {
            Context ctx = GameApp.uiCtx();
            PlayerData p = GameApp.player;
            int maxCount = costType.equals("gold") ? 3 : 6;
            if (p.dailyMysteryCount >= maxCount) {
                U.alert(ctx, null, GameApp.T("انتهت محاولاتك لليوم! عد غداً.", "Your attempts for today are over! Come back tomorrow."), GameApp.T("حسنا", "OK"), null);
                return;
            }
            boolean enough = costType.equals("gold") ? (p.gold >= costVal) : (p.crystals >= costVal);
            if (!enough) {
                U.alert(ctx, null, GameApp.T("الموارد غير كافية!", "Not enough resources!"), GameApp.T("حسنا", "OK"), null);
                return;
            }
            if (costType.equals("gold")) p.gold -= costVal; else p.crystals -= costVal;
            p.dailyMysteryCount += 1;
            int r = NumberUtil.rand(1, 100);
            String msg;
            int luckBonusBox = (int) Math.floor(p.stats.luck * 50);
            if (r <= 50) {
                msg = GameApp.T("الصندوق كان فارغاً!", "The box was empty!");
            } else if (r <= 80) {
                long goldBox = 10000 + luckBonusBox;
                p.gold += goldBox;
                msg = GameApp.T("وجدت", "You found") + goldBox + GameApp.T("ذهب!", "gold!");
                GameApp.sound.playSnd("reward.mp3");
            } else if (r <= 95) {
                PlayerSystem.addExp(15000);
                msg = GameApp.T("وجدت 15,000 خبرة!", "You found 15,000 exp!");
                GameApp.sound.playSnd("reward.mp3");
            } else if (r <= 97) {
                p.crystals += 15;
                msg = GameApp.T("كنز! وجدت 15 كريستال!", "Treasure! You found 15 crystals!");
                GameApp.sound.playSnd("reward.mp3");
            } else {
                p.points += 10;
                msg = GameApp.T("صندوق أسطوري! 10 نقاط تطوير!", "Legendary box! 10 upgrade points!");
                GameApp.sound.playSnd("reward.mp3");
            }
            SaveSystem.saveAndRefresh();
            U.alert(ctx, GameApp.T("صندوق الغموض", "Mystery Box"), msg, GameApp.T("حسنا", "OK"), null);
        } finally {
            mysteryBoxBusy = false;
        }
    }

    public static void checkDailyReset() {
        PlayerData p = GameApp.player;
        String curDay = String.valueOf(ServerTime.now() / 86400);
        if (!p.lastDailyReset.equals(curDay)) {
            p.dailyWheelCount = 0;
            p.dailyMysteryCount = 0;
            p.dailyInvasionFree = 0;
            p.dailyInvasionPaid = 0;
            p.lastDailyReset = curDay;
        }
    }

    public static void travel() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        final String[] areas = {GameApp.T("العفاريت (Level 1)", "Goblins (Level 1)"), GameApp.T("الاسود (Level 20)", "Lions (Level 20)"), GameApp.T("الشياطين (Level 50)", "Demons (Level 50)"), GameApp.T("التنانين (Level 100)", "Dragons (Level 100)"), GameApp.T("بوابة الظلام (Level 500)", "Dark Gate (Level 500)"), GameApp.T("عرش الفراغ (Level 600)", "Throne of the Void (Level 600)"), GameApp.T("المجرة (Level 700)", "Galaxy (Level 700)"), GameApp.T("جزيرة الأرواح (Level 100)", "Spirit Island (Level 100)"), GameApp.T("مملكة السماء (Level 200)", "Sky Kingdom (Level 200)"), GameApp.T("أعماق الهاوية (Level 500)", "Abyss Depths (Level 500)")};
        final int[] reqs = {1, 20, 50, 100, 500, 600, 700, 100, 200, 500};
        final String[] aNames = {"العفاريت", "الاسود", "الشياطين", "التنانين", "بوابة الظلام", "عرش الفراغ", "المجرة", "جزيرة الأرواح", "مملكة السماء", "أعماق الهاوية"};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("سفر (27 ذهب)", "Travel (27 gold)"));
        b.setItems(areas, (d, i) -> {
            if (p.gold >= 27 && p.level >= reqs[i]) {
                p.gold -= 27;
                p.location = aNames[i];
                p.killsInArea = 0;
                GameApp.sound.playSnd("travel.mp3");
                SaveSystem.saveAndRefresh();
                final String dest = aNames[i];
                new AlertDialog.Builder(ctx)
                        .setTitle(null)
                        .setView(U.msg(GameApp.T("وصلت إلى", "You arrived at") + zoneName(dest) + GameApp.T("\n\nسيتم دخولك إلى الأرض مباشرة...", "\n\nYou will enter the land directly...")))
                        .setPositiveButton(GameApp.T("حسنا", "OK"), (d2, w2) -> {
                            d2.dismiss();
                            ExplorationSystem.openExplorationUI();
                        })
                        .setCancelable(false)
                        .show();
            } else {
                U.alert(ctx, null, GameApp.T("الذهب غير كافٍ أو اللفل منخفض!", "Not enough gold or level too low!"), GameApp.T("حسنا", "OK"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    public static void strategicDuelMenu() {
        StrategicDuelSystem.openStrategicDuelMenu();
    }

    public static void mercenaryGuild() {
        Context ctx = GameApp.uiCtx();
        PlayerData p = GameApp.player;
        if (p.currentQuest != null && p.currentQuest.active) {
            if (p.currentQuest.current >= p.currentQuest.target) {
                String rt = p.currentQuest.rewardType;
                long rv = p.currentQuest.rewardVal;
                String msg = "";
                if (rt.equals("gold")) { p.gold += rv; msg = rv + GameApp.T("ذهب", "gold"); }
                else if (rt.equals("exp")) { PlayerSystem.addExp(rv); msg = rv + GameApp.T("خبرة", "exp"); }
                else if (rt.equals("crystal")) { p.crystals += rv; msg = rv + GameApp.T("كريستال", "crystals"); }
                else if (rt.equals("points")) { p.points += rv; msg = rv + GameApp.T("نقاط تطوير", "upgrade points"); }
                else if (rt.equals("multi") && p.currentQuest.rewardObj != null) {
                    JSONObject r = p.currentQuest.rewardObj;
                    long g = r.optLong("gold", 0), e = r.optLong("exp", 0), c = r.optLong("crystal", 0);
                    p.gold += g;
                    PlayerSystem.addExp(e);
                    p.crystals += c;
                    msg = g + GameApp.T("ذهب،", "gold,") + e + GameApp.T("خبرة، و", "exp, and") + c + GameApp.T("كريستال", "crystals");
                } else if (rt.equals("tribe_donate_reward") && p.currentQuest.rewardObj != null) {
                    JSONObject r = p.currentQuest.rewardObj;
                    p.crystals += r.optLong("crystal", 0);
                    p.diamonds += r.optLong("diamond", 0);
                    msg = r.optLong("crystal", 0) + GameApp.T("كريستال و", "crystals and") + r.optLong("diamond", 0) + GameApp.T("ألماس", "diamonds");
                } else {
                    p.gold += (p.currentQuest.rewardVal);
                    msg = GameApp.T("جوائز كلاسيكية", "Classic rewards");
                }
                p.currentQuest.active = false;
                SaveSystem.saveAndRefresh();
                U.alert(ctx, GameApp.T("مبروك!", "Congratulations!"), GameApp.T("استلمت الجائزة:\n", "You claimed the reward:\n") + msg, GameApp.T("حسنا", "OK"), null);
            } else {
                String remainingText = "";
                if (p.currentQuest.type.equals("kill")) remainingText = GameApp.T("وحش", "monster");
                else if (p.currentQuest.type.equals("walk")) remainingText = GameApp.T("خطوة", "step");
                else if (p.currentQuest.type.equals("kill_dragon")) remainingText = GameApp.T("تنين", "dragon");
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setView(U.msg(GameApp.T("باقي:", "Remaining:") + (p.currentQuest.target - p.currentQuest.current) + "" + remainingText + GameApp.T("لإنجاز المهمة", "to complete the quest")));
                b.setPositiveButton(GameApp.T("حسنا", "OK"), null);
                b.setNeutralButton(GameApp.T("إنهاء فوري (3 ألماس)", "Finish Instantly (3 diamonds)"), (d, w) -> {
                    if (p.diamonds >= 3) {
                        p.diamonds -= 3;
                        p.currentQuest.current = p.currentQuest.target;
                        SaveSystem.saveAndRefresh();
                    } else {
                        U.toast(GameApp.T("لا تملك ألماس كافٍ!", "Not enough diamonds!"));
                    }
                });
                b.setNegativeButton(GameApp.T("الانسحاب (10 كريستال)", "Withdraw (10 crystals)"), (d, w) -> {
                    if (p.crystals >= 10) {
                        p.crystals -= 10;
                        p.currentQuest.active = false;
                        SaveSystem.saveAndRefresh();
                        U.alert(ctx, null, GameApp.T("تم الانسحاب من المهمة بنجاح!", "You withdrew from the quest successfully!"), GameApp.T("حسنا", "OK"), null);
                    } else {
                        U.alert(ctx, null, GameApp.T("لا تملك كريستال كافٍ للانسحاب (تحتاج 10 كريستال).", "You don't have enough crystals to withdraw (need 10 crystals)."), GameApp.T("حسنا", "OK"), null);
                    }
                });
                b.show();
            }
        } else {
            String[] qList = {
                    GameApp.T("صيد 15 وحش (الجائزة: 2000 ذهب)", "Hunt 15 monsters (Reward: 2000 gold)"),
                    GameApp.T("المشي 100 خطوة (الجائزة: 3000 خبرة)", "Walk 100 steps (Reward: 3000 exp)"),
                    GameApp.T("صيد 30 وحش (الجائزة: 5 كريستال)", "Hunt 30 monsters (Reward: 5 crystals)"),
                    GameApp.T("المشي 250 خطوة (الجائزة: 2 نقاط تطوير)", "Walk 250 steps (Reward: 2 upgrade points)"),
                    GameApp.T("هزيمة 100 وحش (الجائزة: 10000 ذهب، 5000 خبرة، 10 كريستال)", "Defeat 100 monsters (Reward: 10000 gold, 5000 exp, 10 crystals)"),
                    GameApp.T("المشي 500 خطوة (الجائزة: 15000 ذهب، 7500 خبرة، 15 كريستال)", "Walk 500 steps (Reward: 15000 gold, 7500 exp, 15 crystals)"),
                    GameApp.T("هزيمة 50 تنين (الجائزة: 20000 ذهب، 10000 خبرة، 20 كريستال)", "Defeat 50 dragons (Reward: 20000 gold, 10000 exp, 20 crystals)"),
                    GameApp.T("التبرع بـ 100,000 ذهب للقبيلة (الجائزة: 10 كريستال + 1 ألماس)", "Donate 100,000 gold to the tribe (Reward: 10 crystals + 1 diamond)")
            };
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("اختر مهمة", "Choose a Quest"));
            b.setItems(qList, (d, i) -> {
                Quest q = new Quest();
                q.active = true;
                if (i == 0) { q.type = "kill"; q.target = 15; q.rewardType = "gold"; q.rewardVal = 2000; }
                else if (i == 1) { q.type = "walk"; q.target = 100; q.rewardType = "exp"; q.rewardVal = 3000; }
                else if (i == 2) { q.type = "kill"; q.target = 30; q.rewardType = "crystal"; q.rewardVal = 5; }
                else if (i == 3) { q.type = "walk"; q.target = 250; q.rewardType = "points"; q.rewardVal = 2; }
                else if (i == 4) {
                    q.type = "kill"; q.target = 100; q.rewardType = "multi"; q.title = GameApp.T("هزيمة 100 وحش", "Defeat 100 monsters");
                    try {
                        q.rewardObj = new JSONObject().put("gold", 10000).put("exp", 5000).put("crystal", 10);
                    } catch (JSONException ignored) {}
                } else if (i == 5) {
                    q.type = "walk"; q.target = 500; q.rewardType = "multi"; q.title = GameApp.T("المشي 500 خطوة", "Walk 500 steps");
                    try {
                        q.rewardObj = new JSONObject().put("gold", 15000).put("exp", 7500).put("crystal", 15);
                    } catch (JSONException ignored) {}
                } else if (i == 6) {
                    q.type = "kill_dragon"; q.target = 50; q.rewardType = "multi"; q.title = GameApp.T("هزيمة 50 تنين", "Defeat 50 dragons");
                    try {
                        q.rewardObj = new JSONObject().put("gold", 20000).put("exp", 10000).put("crystal", 20);
                    } catch (JSONException ignored) {}
                } else if (i == 7) {
                    if (p.clan.equals("لا يوجد")) {
                        U.alert(ctx, null, GameApp.T("يجب أن تكون في قبيلة لقبول هذه المهمة!", "You must be in a tribe to accept this quest!"), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    q.type = "donate_tribe"; q.target = 100000; q.rewardType = "tribe_donate_reward"; q.title = GameApp.T("التبرع بـ 100,000 ذهب للقبيلة", "Donate 100,000 gold to your tribe");
                    try {
                        q.rewardObj = new JSONObject().put("crystal", 10).put("diamond", 1);
                    } catch (JSONException ignored) {}
                    p.currentQuest = q;
                    SaveSystem.saveAndRefresh();
                    U.alert(ctx, null, GameApp.T("استلمت المهمة بنجاح: تبرع بـ 100,000 ذهب لقبيلتك!", "Quest accepted: donate 100,000 gold to your tribe!"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                p.currentQuest = q;
                SaveSystem.saveAndRefresh();
                U.alert(ctx, null, GameApp.T("استلمت المهمة بنجاح", "Quest accepted successfully"), GameApp.T("حسنا", "OK"), null);
            });
            b.show();
        }
    }

    public static void leaderboard() {
        Context ctx = GameApp.uiCtx();
        String[] opts = {GameApp.T("أقوى المحاربين", "Strongest Warriors"), GameApp.T("أقوى القبائل", "Strongest Tribes"), GameApp.T("أقوى المنتصرين", "Strongest Winners")};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("قائمة الصدارة", "Leaderboard"));
        b.setItems(opts, (d, ch) -> {
            if (ch == 0) fetchWarriorsLeaderboard(ctx);
            else if (ch == 1) fetchTribesLeaderboard(ctx);
            else fetchWinnersLeaderboard(ctx);
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void fetchWarriorsLeaderboard(Context ctx) {
        ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري جلب قائمة الأساطير...", "Fetching legends list..."), true);
        new Thread(() -> {
            JSONObject dt = Db.get("players");
            new Handler(Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                if (dt == null) {
                    U.alert(ctx, null, GameApp.T("فشل تحميل البيانات", "Failed to load data"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                java.util.Map<String, Double> scores = new java.util.HashMap<>();
                java.util.Map<String, JSONObject> players = new java.util.HashMap<>();
                java.util.Iterator<String> it = dt.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject v = dt.optJSONObject(k);
                    if (v == null) continue;
                    // Hide developer accounts that chose to hide themselves from the public
                    // leaderboard; developers appear by default unless they opt out.
                    if (v.optBoolean("is_dev", false) && !v.optBoolean("leader_visible", true)) continue;
                    String name = v.optString("name", "");
                    if (name.isEmpty()) continue;
                    double power = v.optDouble("total_power", 0);
                    if (power <= 0) {
                        JSONObject st = v.optJSONObject("stats");
                        if (st != null) {
                            double lvl = v.optDouble("level", 1);
                            double lb = 1 + (lvl * 0.02);
                            power = ((st.optDouble("strength", 0) * 10) + (st.optDouble("endurance", 0) * 5)
                                    + (st.optDouble("max_hp", 0) / 2) + (st.optDouble("agility", 0) * 5)
                                    + (st.optDouble("luck", 0) * 5)) * lb;
                        }
                    }
                    scores.put(k, power);
                    players.put(k, v);
                }
                List<String> sorted = new ArrayList<>(scores.keySet());
                sorted.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < Math.min(20, sorted.size()); i++) {
                    String id = sorted.get(i);
                    JSONObject v = players.get(id);
                    double pp = scores.get(id);
                    lines.add((i + 1) + "." + v.optString("name", GameApp.T("لاعب", "Player")) + GameApp.T("| مستوى:", "| Level:") + v.optInt("level", 1) + GameApp.T("| قوة:", "| Power:") + (long) pp);
                }
                if (!lines.isEmpty()) {
                    AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                    b.setTitle(GameApp.T("أساطير العالم (أقوى 20)", "World Legends (Top 20)"));
                    b.setItems(lines.toArray(new String[0]), (d2, pos) -> {
                        String id = sorted.get(pos);
                        JSONObject t = players.get(id);
                        double pp2 = scores.get(id);
                        AlertDialog.Builder b3 = new AlertDialog.Builder(ctx);
                        b3.setTitle(t.optString("name", ""));
                        b3.setView(U.msg(GameApp.T("المستوى:", "Level:") + t.optInt("level", 1) + GameApp.T("\nالقوة:", "\nPower:") + (long) pp2
                                + GameApp.T("\nالذهب:", "\nGold:") + t.optLong("gold", 0) + GameApp.T("\nالقبيلة:", "\nTribe:") + t.optString("clan", "لا يوجد")));
                        b3.show();
                    });
                    b.show();
                } else {
                    U.alert(ctx, null, GameApp.T("لا توجد بيانات متاحة حالياً", "No data available right now"), GameApp.T("حسنا", "OK"), null);
                }
            });
        }).start();
    }

    private static void fetchTribesLeaderboard(Context ctx) {
        ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري جلب قائمة القبائل...", "Fetching tribes list..."), true);
        new Thread(() -> {
            JSONObject dt = Db.get("tribes_system");
            new Handler(Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                if (dt == null) {
                    U.alert(ctx, null, GameApp.T("فشل تحميل بيانات القبائل", "Failed to load tribes data"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                List<String> names = new ArrayList<>();
                java.util.Map<String, JSONObject> tribes = new java.util.HashMap<>();
                java.util.Iterator<String> it = dt.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject v = dt.optJSONObject(k);
                    if (v == null) continue;
                    names.add(k);
                    tribes.put(k, v);
                }
                names.sort((a, b) -> Long.compare(tribes.get(b).optLong("rep", 0), tribes.get(a).optLong("rep", 0)));
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < Math.min(20, names.size()); i++) {
                    JSONObject v = tribes.get(names.get(i));
                    lines.add((i + 1) + "." + names.get(i) + GameApp.T("| مستوى:", "| Level:") + v.optInt("level", 1) + GameApp.T("| سمعة:", "| Reputation:") + v.optLong("rep", 0));
                }
                if (!lines.isEmpty()) {
                    AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                    b.setTitle(GameApp.T("أقوى القبائل (أفضل 20)", "Strongest Tribes (Top 20)"));
                    b.setItems(lines.toArray(new String[0]), null);
                    b.show();
                } else {
                    U.alert(ctx, null, GameApp.T("لا توجد قبائل مسجلة حالياً", "No tribes registered yet"), GameApp.T("حسنا", "OK"), null);
                }
            });
        }).start();
    }

    private static void fetchWinnersLeaderboard(Context ctx) {
        ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري جلب قائمة المنتصرين...", "Fetching winners list..."), true);
        new Thread(() -> {
            JSONObject medals = Db.get("medals");
            new Handler(Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                if (medals == null) {
                    U.alert(ctx, null, GameApp.T("لا توجد معارك ساحة بعد", "No arena battles yet"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                java.util.Map<String, JSONObject> winners = new java.util.HashMap<>();
                java.util.Iterator<String> it = medals.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject v = medals.optJSONObject(k);
                    if (v == null) continue;
                    String name = v.optString("name", "");
                    if (name.isEmpty()) continue;
                    winners.put(k, v);
                }
                if (winners.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا توجد معارك ساحة بعد", "No arena battles yet"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                List<String> sorted = new ArrayList<>(winners.keySet());
                sorted.sort((a, b) -> Long.compare(winners.get(b).optLong("wins", 0), winners.get(a).optLong("wins", 0)));
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < Math.min(20, sorted.size()); i++) {
                    String id = sorted.get(i);
                    JSONObject v = winners.get(id);
                    lines.add((i + 1) + "." + v.optString("name", GameApp.T("لاعب", "Player")) + GameApp.T("| انتصارات:", "| Wins:") + v.optLong("wins", 0) + GameApp.T("| ميداليات:", "| Medals:") + v.optLong("count", 0));
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("أقوى المنتصرين (أفضل 20)", "Strongest Winners (Top 20)"));
                b.setItems(lines.toArray(new String[0]), null);
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                b.show();
            });
        }).start();
    }
}
