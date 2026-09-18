package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.lli.com.GameApp;
import com.lli.com.core.Db;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class TribeSystem {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String TRIBES_PATH = "tribes_system/";
    private static final String USER_PATH = "players/";

    private static class Skill {
        String id;
        String name;
        String desc;
        int cost;
        int maxLv;

        Skill(String id, String name, String desc, int cost, int maxLv) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.cost = cost;
            this.maxLv = maxLv;
        }
    }

    private static final Skill[] TRIBE_SKILLS = {
            new Skill("atk_boost", GameApp.T("راية الحرب", "War Banner"), GameApp.T("زيادة الهجوم لجميع الأعضاء بنسبة 5% لكل مستوى", "Increases attack for all members by 5% per level"), 100, 10),
            new Skill("def_boost", GameApp.T("درع القبيلة", "Tribe Shield"), GameApp.T("زيادة الدفاع لجميع الأعضاء بنسبة 5% لكل مستوى", "Increases defense for all members by 5% per level"), 100, 10),
            new Skill("exp_boost", GameApp.T("حكمة القدماء", "Wisdom of the Ancients"), GameApp.T("زيادة الخبرة المكتسبة بنسبة 10% لكل مستوى", "Increases earned experience by 10% per level"), 150, 5),
            new Skill("gold_boost", GameApp.T("ثروة الملوك", "Wealth of Kings"), GameApp.T("زيادة الذهب المكتسب بنسبة 10% لكل مستوى", "Increases earned gold by 10% per level"), 150, 5)
    };

    private static class Relic {
        String id;
        String name;
        String desc;
        String rarity;

        Relic(String id, String name, String desc, String rarity) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.rarity = rarity;
        }
    }

    private static final Relic[] SACRED_RELICS = {
            new Relic("relic_hp", GameApp.T("حجر الحياة الأبدي", "Eternal Life Stone"), GameApp.T("يستعيد 2% من الصحة كل دور في القتال", "Restores 2% of health every turn in battle"), "أسطوري"),
            new Relic("relic_crit", GameApp.T("عين الصقر الأسطورية", "Legendary Falcon Eye"), GameApp.T("يزيد فرصة الضربات الحرجة بنسبة 10%", "Increases critical hit chance by 10%"), "ملحمي"),
            new Relic("relic_luck", GameApp.T("تميمة الحظ العاثر", "Unlucky Luck Charm"), GameApp.T("يزيد فرصة سقوط المعدات النادرة بنسبة 15%", "Increases rare equipment drop chance by 15%"), "نادر")
    };

    private static class Building {
        String id;
        String name;
        String desc;

        Building(String id, String name, String desc) {
            this.id = id;
            this.name = name;
            this.desc = desc;
        }
    }

    private static final Building[] TRIBE_BUILDINGS = {
            new Building("gold_mine", GameApp.T("منجم الذهب الملكي", "Royal Gold Mine"), GameApp.T("يزيد إنتاج الذهب للأعضاء", "Increases gold production for members")),
            new Building("training_camp", GameApp.T("معسكر التدريب الأسطوري", "Legendary Training Camp"), GameApp.T("يزيد الخبرة المكتسبة", "Increases earned experience")),
            new Building("armory", GameApp.T("مخزن الأسلحة", "Weapon Storage"), GameApp.T("يزيد قوة الهجوم الكلية", "Increases total attack power")),
            new Building("fortress", GameApp.T("الحصن المنيع", "The Impenetrable Fortress"), GameApp.T("يزيد قوة الدفاع الكلية", "Increases total defense power")),
            new Building("altar", GameApp.T("مذبح الأرواح", "Altar of Souls"), GameApp.T("يزيد فرصة الضربات الحرجة", "Increases critical hit chance")),
            new Building("market", GameApp.T("السوق المركزي", "Central Market"), GameApp.T("يقلل تكاليف الشراء من المتجر", "Reduces store purchase costs"))
    };

    private static final String[][] BUILDING_BENEFITS = {
            {GameApp.T("+5% ذهب", "+5% Gold"), GameApp.T("+10% ذهب", "+10% Gold"), GameApp.T("+15% ذهب", "+15% Gold"), GameApp.T("+20% ذهب", "+20% Gold"), GameApp.T("+25% ذهب", "+25% Gold"), GameApp.T("+30% ذهب", "+30% Gold"), GameApp.T("+35% ذهب", "+35% Gold"), GameApp.T("+40% ذهب", "+40% Gold"), GameApp.T("+45% ذهب", "+45% Gold"), GameApp.T("+50% ذهب", "+50% Gold")},
            {GameApp.T("+5% خبرة", "+5% Experience"), GameApp.T("+10% خبرة", "+10% Experience"), GameApp.T("+15% خبرة", "+15% Experience"), GameApp.T("+20% خبرة", "+20% Experience"), GameApp.T("+25% خبرة", "+25% Experience"), GameApp.T("+30% خبرة", "+30% Experience"), GameApp.T("+35% خبرة", "+35% Experience"), GameApp.T("+40% خبرة", "+40% Experience"), GameApp.T("+45% خبرة", "+45% Experience"), GameApp.T("+50% خبرة", "+50% Experience")},
            {GameApp.T("+2% هجوم", "+2% Attack"), GameApp.T("+4% هجوم", "+4% Attack"), GameApp.T("+6% هجوم", "+6% Attack"), GameApp.T("+8% هجوم", "+8% Attack"), GameApp.T("+10% هجوم", "+10% Attack"), GameApp.T("+12% هجوم", "+12% Attack"), GameApp.T("+14% هجوم", "+14% Attack"), GameApp.T("+16% هجوم", "+16% Attack"), GameApp.T("+18% هجوم", "+18% Attack"), GameApp.T("+20% هجوم", "+20% Attack")},
            {GameApp.T("+2% دفاع", "+2% Defense"), GameApp.T("+4% دفاع", "+4% Defense"), GameApp.T("+6% دفاع", "+6% Defense"), GameApp.T("+8% دفاع", "+8% Defense"), GameApp.T("+10% دفاع", "+10% Defense"), GameApp.T("+12% دفاع", "+12% Defense"), GameApp.T("+14% دفاع", "+14% Defense"), GameApp.T("+16% دفاع", "+16% Defense"), GameApp.T("+18% دفاع", "+18% Defense"), GameApp.T("+20% دفاع", "+20% Defense")},
            {GameApp.T("+1% حرج", "+1% Critical"), GameApp.T("+2% حرج", "+2% Critical"), GameApp.T("+3% حرج", "+3% Critical"), GameApp.T("+4% حرج", "+4% Critical"), GameApp.T("+5% حرج", "+5% Critical"), GameApp.T("+6% حرج", "+6% Critical"), GameApp.T("+7% حرج", "+7% Critical"), GameApp.T("+8% حرج", "+8% Critical"), GameApp.T("+9% حرج", "+9% Critical"), GameApp.T("+10% حرج", "+10% Critical")},
            {GameApp.T("-2% تكلفة", "-2% Cost"), GameApp.T("-4% تكلفة", "-4% Cost"), GameApp.T("-6% تكلفة", "-6% Cost"), GameApp.T("-8% تكلفة", "-8% Cost"), GameApp.T("-10% تكلفة", "-10% Cost"), GameApp.T("-12% تكلفة", "-12% Cost"), GameApp.T("-14% تكلفة", "-14% Cost"), GameApp.T("-16% تكلفة", "-16% Cost"), GameApp.T("-18% تكلفة", "-18% Cost"), GameApp.T("-20% تكلفة", "-20% Cost")}
    };

    private static final String[] GUARDIAN_BENEFITS = {
            GameApp.T("+5% قوة هجوم القبيلة", "+5% Tribe Attack Power"), GameApp.T("+5% قوة دفاع القبيلة", "+5% Tribe Defense Power"), GameApp.T("+10% صحة إضافية للأعضاء", "+10% Extra Health for Members"),
            GameApp.T("فرصة 5% لتفادي الضربات", "5% Chance to Dodge Attacks"), GameApp.T("+10% ضرر حرج", "+10% Critical Damage"), GameApp.T("استعادة 2% صحة كل دور", "Restore 2% Health Every Turn"),
            GameApp.T("+15% قوة هجوم الحارس في الغزو", "+15% Guardian Attack Power in Raids"), GameApp.T("+15% قوة دفاع الحارس في الدفاع", "+15% Guardian Defense Power in Defense"),
            GameApp.T("تقليل ضرر الزعماء بنسبة 10%", "Reduce Boss Damage by 10%"), GameApp.T("هدية الحارس: مضاعفة الجوائز اليومية", "Guardian's Gift: Double Daily Rewards")
    };

    public static void openTribeUI() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        if (player.clan == null || player.clan.equals("لا يوجد")) {
            String[] opts = {GameApp.T("تأسيس قبيلة (100,000 ذهب، 100 كريستال، 15 ألماس)", "Establish Tribe (100,000 gold, 100 crystals, 15 diamonds)"), GameApp.T("استعراض القبائل للانضمام", "Browse Tribes to Join")};
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("القبيلة", "Tribe"));
            b.setItems(opts, (d, i) -> {
                if (i == 0) {
                    createTribe();
                } else {
                    browseTribes();
                }
            });
            b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            b.show();
        } else {
            openTribePanel();
        }
    }

    private static void createTribe() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final EditText e = new EditText(ctx);
        e.setHint(GameApp.T("اسم القبيلة...", "Tribe name..."));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("تأسيس قبيلة", "Establish Tribe"));
        b.setView(e);
        b.setPositiveButton(GameApp.T("تأسيس", "Establish"), (d, w) -> {
            final String covName = e.getText().toString().trim();
            if (covName.isEmpty()) return;
            if (player.gold >= 100000 && player.crystals >= 100 && player.diamonds >= 15) {
                player.gold -= 100000;
                player.crystals -= 100;
                player.diamonds -= 15;
                player.clan = covName;
                JSONObject initCov = new JSONObject();
                try {
                    initCov.put("leader", player.name);
                    initCov.put("level", 1);
                    initCov.put("exp", 0);
                    initCov.put("max_exp", 1000);
                    initCov.put("gold", 0);
                    initCov.put("essence", 50);
                    JSONObject skills = new JSONObject();
                    skills.put("atk_boost", 0);
                    skills.put("def_boost", 0);
                    skills.put("exp_boost", 0);
                    skills.put("gold_boost", 0);
                    initCov.put("skills", skills);
                    initCov.put("relics", new JSONArray());
                    JSONObject members = new JSONObject();
                    JSONObject me = new JSONObject();
                    me.put("name", player.name);
                    me.put("role", "leader");
                    members.put(player.username, me);
                    initCov.put("members", members);
                    initCov.put("members_count", 1);
                    initCov.put("description", GameApp.T("قبيلة أسطورية جديدة تسعى للقمة.", "A new legendary tribe striving for the top."));
                } catch (Exception ignored) {}
                final JSONObject fc = initCov;
                new Thread(() -> {
                    Db.put(TRIBES_PATH + Db.encode(covName), fc);
                    SaveSystem.saveAndRefresh();
                    UI.post(() -> U.alert(ctx, null, GameApp.T("تم تأسيس قبيلة [" + covName + "] بنجاح!", "Tribe [" + covName + "] established successfully!"), GameApp.T("حسناً", "OK"), null));
                }).start();
            } else {
                U.alert(ctx, null, GameApp.T("الموارد غير كافية! تحتاج (100,000 ذهب، 100 كريستال، 15 ألماس)", "Not enough resources! You need (100,000 gold, 100 crystals, 15 diamonds)"), GameApp.T("حسنا", "OK"), null);
            }
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void browseTribes() {
        final Context ctx = GameApp.uiCtx();
        final ProgressDialog progress = U.progress(ctx, GameApp.T("القبائل", "Tribes"), GameApp.T("جاري جلب قائمة القبائل...", "Fetching the tribes list..."), true);
        new Thread(() -> {
            JSONObject allTribes = Db.get(TRIBES_PATH.substring(0, TRIBES_PATH.length() - 1));
            UI.post(() -> {
                progress.dismiss();
                if (allTribes == null) {
                    U.alert(ctx, null, GameApp.T("لا توجد قبائل حالياً.", "There are no tribes currently."), GameApp.T("حسناً", "OK"), null);
                    return;
                }
                final List<String> tNames = new ArrayList<>();
                final List<String> tKeys = new ArrayList<>();
                Iterator<String> keys = allTribes.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONObject data = allTribes.optJSONObject(name);
                    if (data != null) {
                        tNames.add(GameApp.T(name + "(مستوى" + data.optInt("level", 1) + ")", name + "(Level" + data.optInt("level", 1) + ")"));
                        tKeys.add(name);
                    }
                }
                if (tNames.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا توجد قبائل حالياً.", "There are no tribes currently."), GameApp.T("حسناً", "OK"), null);
                    return;
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("اختر قبيلة للانضمام", "Choose a Tribe to Join"));
                b.setItems(tNames.toArray(new String[0]), (d, idx) -> joinTribe(tKeys.get(idx)));
                b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                b.show();
            });
        }).start();
    }

    private static void joinTribe(final String clanName) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        new Thread(() -> {
            JSONObject tribeData = Db.get(TRIBES_PATH + Db.encode(clanName));
            if (tribeData == null) {
                UI.post(() -> U.alert(ctx, null, GameApp.T("خطأ في بيانات القبيلة", "Error in tribe data"), GameApp.T("حسناً", "OK"), null));
                return;
            }
            JSONObject members = tribeData.optJSONObject("members");
            if (members == null) {
                members = new JSONObject();
                try {
                    tribeData.put("members", members);
                } catch (Exception ignored) {}
            }
            try {
                JSONObject me = new JSONObject();
                me.put("name", player.name);
                me.put("role", "member");
                members.put(player.username, me);
                int count = members.length();
                tribeData.put("members_count", count);
            } catch (Exception ignored) {}
            Db.put(TRIBES_PATH + Db.encode(clanName), tribeData);
            player.clan = clanName;
            SaveSystem.saveAndRefresh();
            UI.post(() -> U.alert(ctx, null, GameApp.T("مرحباً بك في قبيلة [" + clanName + "]!", "Welcome to the tribe [" + clanName + "]!"), GameApp.T("حسناً", "OK"), null));
        }).start();
    }

    private static void openTribePanel() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final ProgressDialog progress = U.progress(ctx, GameApp.T("القبيلة", "Tribe"), GameApp.T("جاري فتح مقر القبيلة...", "Opening the tribe headquarters..."), true);
        new Thread(() -> {
            JSONObject cov = Db.get(TRIBES_PATH + Db.encode(player.clan));
            UI.post(() -> {
                progress.dismiss();
                if (cov == null) {
                    player.clan = "لا يوجد";
                    SaveSystem.saveAndRefresh();
                    return;
                }
                JSONObject members = cov.optJSONObject("members");
                if (members == null) {
                    members = new JSONObject();
                    try {
                        JSONObject me = new JSONObject();
                        me.put("name", player.name);
                        me.put("role", "leader");
                        members.put(player.username, me);
                        cov.put("members", members);
                    } catch (Exception ignored) {}
                }
                String myRole = "member";
                JSONObject myMember = members.optJSONObject(player.username);
                if (myMember != null) myRole = myMember.optString("role", "member");
                final String role = myRole;

                JSONObject skills = cov.optJSONObject("skills");
                int atkB = skills != null ? skills.optInt("atk_boost", 0) : 0;
                int defB = skills != null ? skills.optInt("def_boost", 0) : 0;
                int expB = skills != null ? skills.optInt("exp_boost", 0) : 0;
                int goldB = skills != null ? skills.optInt("gold_boost", 0) : 0;

                String roleName = role.equals("leader") ? GameApp.T("قائد", "Leader") : (role.equals("elder") ? GameApp.T("مشرف", "Moderator") : GameApp.T("عضو", "Member"));
                String msg = String.format(Locale.US,
                        GameApp.T("القبيلة: %s (مستوى %d)\nالخبرة: %d/%d\nجوهر القبيلة: %d | ذهب الخزينة: %d\nالقائد: %s | رتبتك: %s\n\nالمهارات النشطة:\n- الهجوم: +%d%% | الدفاع: +%d%%\n- الخبرة: +%d%% | الذهب: +%d%%",
                                "Tribe: %s (Level %d)\nExperience: %d/%d\nTribe Essence: %d | Treasury Gold: %d\nLeader: %s | Your Rank: %s\n\nActive Skills:\n- Attack: +%d%% | Defense: +%d%%\n- Experience: +%d%% | Gold: +%d%%"),
                        player.clan, cov.optInt("level", 1), cov.optInt("exp", 0), cov.optInt("max_exp", 1000),
                        cov.optInt("essence", 0), cov.optInt("gold", 0), cov.optString("leader", GameApp.T("غير محدد", "Unknown")),
                        roleName, atkB * 5, defB * 5, expB * 10, goldB * 10);

                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("القبيلة", "Tribe"));
                b.setView(U.msg(msg));
                b.setPositiveButton(GameApp.T("لوحة التحكم", "Control Panel"), (d, w) -> showTribeOptions(cov, role));
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                b.show();
            });
        }).start();
    }

    private static void showTribeOptions(final JSONObject cov, final String myRole) {
        final Context ctx = GameApp.uiCtx();
        List<String> opts = new ArrayList<>();
        if (myRole.equals("leader") || myRole.equals("elder")) opts.add(GameApp.T("إدارة القبيلة", "Tribe Management"));
        opts.add(GameApp.T("سجل أخبار القبيلة", "Tribe News Log"));
        opts.add(GameApp.T("تبرع بالذهب", "Donate Gold"));
        opts.add(GameApp.T("شجرة مهارات القبيلة", "Tribe Skill Tree"));
        opts.add(GameApp.T("مباني القبيلة", "Tribe Buildings"));
        opts.add(GameApp.T("حارس القبيلة", "Tribe Guardian"));
        opts.add(GameApp.T("الهجوم على القبائل", "Attack Tribes"));
        opts.add(GameApp.T("ركن الآثار الأسطورية", "Legendary Relics Corner"));
        opts.add(GameApp.T("المهام التعاونية", "Cooperative Quests"));
        opts.add(GameApp.T("أعضاء القبيلة", "Tribe Members"));
        opts.add(GameApp.T("مغادرة القبيلة", "Leave Tribe"));
        if (myRole.equals("leader")) opts.add(GameApp.T("حذف القبيلة نهائياً 🗑️", "Delete Tribe Permanently 🗑️"));

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("خيارات القبيلة", "Tribe Options"));
        b.setItems(opts.toArray(new String[0]), (d, i) -> {
            String choice = opts.get(i);
            if (choice.equals(GameApp.T("إدارة القبيلة", "Tribe Management"))) {
                openTribeAdminUI(cov, myRole);
            } else if (choice.equals(GameApp.T("سجل أخبار القبيلة", "Tribe News Log"))) {
                openTribeNews(cov);
            } else if (choice.equals(GameApp.T("تبرع بالذهب", "Donate Gold"))) {
                openTribeDonation(cov);
            } else if (choice.equals(GameApp.T("شجرة مهارات القبيلة", "Tribe Skill Tree"))) {
                openTribeSkills(cov, myRole);
            } else if (choice.equals(GameApp.T("مباني القبيلة", "Tribe Buildings"))) {
                openTribeBuildings(cov, myRole);
            } else if (choice.equals(GameApp.T("حارس القبيلة", "Tribe Guardian"))) {
                openGuardianUI(cov);
            } else if (choice.equals(GameApp.T("الهجوم على القبائل", "Attack Tribes"))) {
                if (!myRole.equals("leader") && !myRole.equals("elder")) {
                    U.alert(ctx, null, GameApp.T("فقط القادة والمشرفون يمكنهم شن الهجمات.", "Only leaders and moderators can launch attacks."), GameApp.T("حسناً", "OK"), null);
                    return;
                }
                openTribeWarUI();
            } else if (choice.equals(GameApp.T("ركن الآثار الأسطورية", "Legendary Relics Corner"))) {
                openRelicAltar(cov);
            } else if (choice.equals(GameApp.T("المهام التعاونية", "Cooperative Quests"))) {
                openCooperativeQuests(cov);
            } else if (choice.equals(GameApp.T("أعضاء القبيلة", "Tribe Members"))) {
                openTribeMembersUI(cov, myRole);
            } else if (choice.equals(GameApp.T("مغادرة القبيلة", "Leave Tribe"))) {
                leaveTribe(cov, myRole);
            } else if (choice.equals(GameApp.T("حذف القبيلة نهائياً 🗑️", "Delete Tribe Permanently 🗑️"))) {
                deleteTribe(cov);
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void openTribeSkills(final JSONObject cov, final String myRole) {
        final Context ctx = GameApp.uiCtx();
        List<String> names = new ArrayList<>();
        for (Skill s : TRIBE_SKILLS) {
            int curLv = cov.optJSONObject("skills") != null ? cov.optJSONObject("skills").optInt(s.id, 0) : 0;
            names.add(s.name + "(Lv" + curLv + "/" + s.maxLv + ")");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("شجرة مهارات القبيلة", "Tribe Skill Tree"));
        b.setItems(names.toArray(new String[0]), (d, i) -> {
            final Skill s = TRIBE_SKILLS[i];
            int curLv = cov.optJSONObject("skills") != null ? cov.optJSONObject("skills").optInt(s.id, 0) : 0;
            if (curLv < s.maxLv) {
                final int cost = s.cost * (curLv + 1);
                AlertDialog.Builder d2 = new AlertDialog.Builder(ctx);
                d2.setTitle(s.name);
                d2.setView(U.msg(s.desc + GameApp.T("\n\nتكلفة التطوير:", "\n\nUpgrade Cost:") + cost + GameApp.T("جوهر قبيلة", "tribe essence")));
                d2.setPositiveButton(GameApp.T("تطوير", "Upgrade"), (d3, w) -> {
                    if (!myRole.equals("leader") && !myRole.equals("elder")) {
                        U.alert(ctx, null, GameApp.T("فقط القادة والمشرفون يمكنهم تطوير المهارات.", "Only leaders and moderators can upgrade skills."), GameApp.T("حسناً", "OK"), null);
                        return;
                    }
                    int essence = cov.optInt("essence", 0);
                    if (essence >= cost) {
                        try {
                            cov.put("essence", essence - cost);
                            JSONObject skills = cov.optJSONObject("skills");
                            if (skills == null) {
                                skills = new JSONObject();
                                cov.put("skills", skills);
                            }
                            skills.put(s.id, curLv + 1);
                        } catch (Exception ignored) {}
                        new Thread(() -> Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov)).start();
                        U.alert(ctx, null, GameApp.T("تم تطوير المهارة بنجاح!", "Skill upgraded successfully!"), GameApp.T("حسناً", "OK"), null);
                    } else {
                        U.toast(GameApp.T("جوهر القبيلة غير كافٍ!", "Not enough tribe essence!"));
                    }
                });
                d2.show();
            } else {
                U.toast(GameApp.T("المهارة في المستوى الأقصى!", "The skill is at its maximum level!"));
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void openTribeDonation(final JSONObject cov) {
        final Context ctx = GameApp.uiCtx();
        String[] opts = {GameApp.T("تبرع بالذهب", "Donate Gold"), GameApp.T("تبرع بالكريستال", "Donate Crystals"), GameApp.T("تبرع بالألماس", "Donate Diamonds")};
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("التبرع للقبيلة", "Donate to the Tribe"));
        b.setItems(opts, (d, i) -> {
            String resourceName = i == 0 ? GameApp.T("ذهب", "Gold") : (i == 1 ? GameApp.T("كريستال", "Crystal") : GameApp.T("ألماس", "Diamond"));
            final EditText e = new EditText(ctx);
            e.setHint(GameApp.T("كمية", "Amount of") + resourceName + GameApp.T("للتبرع...", "to donate..."));
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            AlertDialog.Builder d2 = new AlertDialog.Builder(ctx);
            d2.setTitle(GameApp.T("تبرع بـ", "Donate with") + resourceName + GameApp.T("للقبيلة", "to the Tribe"));
            LinearLayout lay = U.linear(ctx, true);
            lay.addView(U.msg(GameApp.T("تبرعك سيُضاف مباشرة لموارد القبيلة ويُستخدم في تطوير المباني.", "Your donation will be added directly to the tribe resources and used to upgrade buildings.")));
            lay.addView(e);
            d2.setView(lay);
            d2.setPositiveButton(GameApp.T("تبرع الآن", "Donate Now"), (d3, w) -> {
                long amt;
                try {
                    amt = Long.parseLong(e.getText().toString().trim());
                } catch (Exception ex) {
                    amt = 0;
                }
                if (amt <= 0 || amt > 1_000_000_000) { // Max 1B to prevent overflow
                    U.alert(ctx, null, GameApp.T("الكمية غير صالحة! (الحد الأقصى: 1 مليار)", "Invalid amount! (Max: 1B)"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                PlayerData player = GameApp.player;
                final int resourceIdx = i;
                if (i == 0) {
                    if (player.gold < amt) { U.alert(ctx, null, GameApp.T("ذهبك غير كافِ!", "Your gold is not enough!"), GameApp.T("حسنا", "OK"), null); return; }
                    player.gold -= amt;
                    try { 
                        long currentGold = cov.optLong("gold", 0);
                        cov.put("gold", currentGold + amt); 
                    } catch (Exception ignored) {}
                    addTribeLog(cov, GameApp.T(player.name + "تبرع بـ" + amt + "ذهب للقبيلة.", player.name + "donated" + amt + "gold to the tribe."));
                } else if (i == 1) {
                    if (player.crystals < amt) { U.alert(ctx, null, GameApp.T("كريستالك غير كافِ!", "Your crystals are not enough!"), GameApp.T("حسنا", "OK"), null); return; }
                    player.crystals -= amt;
                    try { 
                        long currentCrystals = cov.optLong("tribe_crystals", 0);
                        cov.put("tribe_crystals", currentCrystals + amt); 
                    } catch (Exception ignored) {}
                    addTribeLog(cov, GameApp.T(player.name + "تبرع بـ" + amt + "كريستال للقبيلة.", player.name + "donated" + amt + "crystals to the tribe."));
                } else {
                    if (player.diamonds < amt) { U.alert(ctx, null, GameApp.T("ألماسك غير كافِ!", "Your diamonds are not enough!"), GameApp.T("حسنا", "OK"), null); return; }
                    player.diamonds -= amt;
                    try { 
                        long currentDiamonds = cov.optLong("tribe_diamonds", 0);
                        cov.put("tribe_diamonds", currentDiamonds + amt); 
                    } catch (Exception ignored) {}
                    addTribeLog(cov, GameApp.T(player.name + "تبرع بـ" + amt + "ألماس للقبيلة.", player.name + "donated" + amt + "diamonds to the tribe."));
                }
                final long donated = amt;
                final String res = resourceName;
                new Thread(() -> {
                    Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov);
                    SaveSystem.saveAndRefresh();
                    UI.post(() -> U.alert(ctx, GameApp.T("شكراً لتبرعك!", "Thank you for donating!"), GameApp.T("تم إضافة" + donated + "" + res + "لموارد القبيلة بنجاح!", donated + "" + res + "added to the tribe resources successfully!"), GameApp.T("حسناً", "OK"), null));
                }).start();
            });
            d2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
            d2.show();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        b.show();
    }

    private static void addTribeLog(JSONObject cov, String msg) {
        JSONArray logs = cov.optJSONArray("logs");
        if (logs == null) logs = new JSONArray();
        JSONArray newLogs = new JSONArray();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        newLogs.put("[" + timestamp + "]" + msg);
        for (int i = 0; i < Math.min(logs.length(), 49); i++) newLogs.put(logs.optString(i));
        try { cov.put("logs", newLogs); } catch (Exception ignored) {}
    }

    private static void openTribeNews(JSONObject cov) {
        final Context ctx = GameApp.uiCtx();
        JSONArray logs = cov.optJSONArray("logs");
        List<String> items = new ArrayList<>();
        if (logs == null || logs.length() == 0) {
            items.add(GameApp.T("لا توجد أخبار حالياً.", "There are no news currently."));
        } else {
            for (int i = 0; i < logs.length(); i++) items.add(logs.optString(i));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("سجل أخبار القبيلة", "Tribe News Log"));
        b.setItems(items.toArray(new String[0]), null);
        b.setPositiveButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void openTribeBuildings(final JSONObject cov, final String myRole) {
        final Context ctx = GameApp.uiCtx();
        List<String> names = new ArrayList<>();
        for (Building bd : TRIBE_BUILDINGS) {
            int lv = cov.optJSONObject("buildings") != null ? cov.optJSONObject("buildings").optInt(bd.id, 0) : 0;
            names.add(GameApp.T(bd.name + "(مستوى" + lv + ")", bd.name + "(Level" + lv + ")"));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("مباني القبيلة", "Tribe Buildings"));
        b.setItems(names.toArray(new String[0]), (d, i) -> {
            final Building bd = TRIBE_BUILDINGS[i];
            int lv = cov.optJSONObject("buildings") != null ? cov.optJSONObject("buildings").optInt(bd.id, 0) : 0;
            StringBuilder msg = new StringBuilder(bd.desc + GameApp.T("\n\nالفوائد المفتوحة:\n", "\n\nUnlocked Benefits:\n"));
            for (int j = 1; j <= 10; j++) {
                msg.append(GameApp.T("مستوى", "Level")).append(j * 10).append(":").append(BUILDING_BENEFITS[i][j - 1]).append("\n");
            }
            final int cost = (lv + 1) * 500;
            AlertDialog.Builder d2 = new AlertDialog.Builder(ctx);
            d2.setTitle(bd.name);
            d2.setView(U.msg(msg + GameApp.T("\nتكلفة التطوير للمستوى التالي:", "\nUpgrade cost for the next level:") + cost + GameApp.T("جوهر", "essence")));
            d2.setPositiveButton(GameApp.T("تطوير", "Upgrade"), (d3, w) -> {
                if (!myRole.equals("leader") && !myRole.equals("elder")) {
                    U.alert(ctx, null, GameApp.T("فقط القادة والمشرفون يمكنهم تطوير المباني.", "Only leaders and moderators can upgrade buildings."), GameApp.T("حسناً", "OK"), null);
                    return;
                }
                int essence = cov.optInt("essence", 0);
                if (essence >= cost) {
                    try {
                        cov.put("essence", essence - cost);
                        JSONObject buildings = cov.optJSONObject("buildings");
                        if (buildings == null) {
                            buildings = new JSONObject();
                            cov.put("buildings", buildings);
                        }
                        buildings.put(bd.id, lv + 1);
                    } catch (Exception ignored) {}
                    new Thread(() -> Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov)).start();
                    U.toast(GameApp.T("تم تطوير المبنى!", "Building upgraded!"));
                } else {
                    U.toast(GameApp.T("جوهر غير كافٍ!", "Not enough essence!"));
                }
            });
            d2.show();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void openGuardianUI(final JSONObject cov) {
        final Context ctx = GameApp.uiCtx();
        JSONObject g = cov.optJSONObject("guardian");
        if (g == null) {
            g = new JSONObject();
            try {
                g.put("level", 1);
                g.put("hp", 10000);
                g.put("max_hp", 10000);
                g.put("atk", 500);
                g.put("wins", 0);
                cov.put("guardian", g);
            } catch (Exception ignored) {}
        }
        final JSONObject guard = g;
        StringBuilder msg = new StringBuilder(String.format(Locale.US,
                GameApp.T("حارس القبيلة (مستوى %d)\nالصحة: %d/%d\nالقوة: %d\nالانتصارات: %d\n\nالفوائد المفتوحة:\n",
                        "Tribe Guardian (Level %d)\nHealth: %d/%d\nPower: %d\nWins: %d\n\nUnlocked Benefits:\n"),
                guard.optInt("level", 1), (long) guard.optDouble("hp", 10000), (long) guard.optDouble("max_hp", 10000),
                (long) guard.optDouble("atk", 500), guard.optInt("wins", 0)));
        int gLevel = guard.optInt("level", 1);
        for (int i = 1; i <= 10; i++) {
            msg.append(GameApp.T("مستوى", "Level")).append(i * 10).append(":").append(GUARDIAN_BENEFITS[i - 1]).append("\n");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("حارس القبيلة", "Tribe Guardian"));
        b.setView(U.msg(msg.toString()));
        b.setPositiveButton(GameApp.T("قتال الحارس (للتطوير)", "Fight Guardian (to upgrade)"), (d, w) -> startGuardianBattle(cov, guard));
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void startGuardianBattle(final JSONObject cov, final JSONObject guard) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        double pAtk = player.totalPower / 10.0;
        double gAtk = guard.optDouble("atk", 500);
        double pHp = player.stats.hp;
        double gHp = guard.optDouble("hp", 10000);
        while (pHp > 0 && gHp > 0) {
            gHp = gHp - pAtk;
            if (gHp <= 0) break;
            pHp = pHp - gAtk;
        }
        if (gHp <= 0) {
            int wins = guard.optInt("wins", 0) + 1;
            int level = guard.optInt("level", 1) + 1;
            double maxHp = guard.optDouble("max_hp", 10000) + 5000;
            double atk = guard.optDouble("atk", 500) + 200;
            try {
                guard.put("wins", wins);
                guard.put("level", level);
                guard.put("max_hp", maxHp);
                guard.put("hp", maxHp);
                guard.put("atk", atk);
            } catch (Exception ignored) {}
            new Thread(() -> Db.put(TRIBES_PATH + Db.encode(player.clan), cov)).start();
            U.alert(ctx, GameApp.T("نصر!", "Victory!"), GameApp.T("هزمتم الحارس! ارتقى للمستوى" + level + "وسيعود أقوى في المرة القادمة.", "You defeated the guardian! It rose to level" + level + "and will return stronger next time."), GameApp.T("حسنا", "OK"), null);
        } else {
            U.alert(ctx, GameApp.T("هزيمة!", "Defeat!"), GameApp.T("الحارس قوي جداً، حاول مرة أخرى بعد زيادة قوتك.", "The guardian is too strong, try again after increasing your power."), GameApp.T("حسنا", "OK"), null);
        }
    }

    private static void openTribeWarUI() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final ProgressDialog progress = U.progress(ctx, GameApp.T("تحميل", "Loading"), GameApp.T("جاري التحقق من جاهزية الجيش...", "Checking the army readiness..."), true);
        new Thread(() -> {
            JSONObject myCov = Db.get(TRIBES_PATH + Db.encode(player.clan));
            UI.post(() -> {
                progress.dismiss();
                if (myCov == null) return;
                long lastAttack = myCov.optLong("last_attack_time", 0);
                long curTime = System.currentTimeMillis() / 1000;
                long cooldown = 3 * 3600;
                if (curTime - lastAttack < cooldown) {
                    long remaining = cooldown - (curTime - lastAttack);
                    long h = remaining / 3600;
                    long m = (remaining % 3600) / 60;
                    U.alert(ctx, GameApp.T("الجيش متعب", "The Army Is Tired"), String.format(Locale.US,
                            GameApp.T("قام أحد أعضاء القبيلة بالهجوم مؤخراً. يجب الانتظار %d ساعة و %d دقيقة قبل الهجوم القادم.", "A tribe member recently attacked. You must wait %d hours and %d minutes before the next attack."), h, m), GameApp.T("حسناً", "OK"), null);
                    return;
                }
                final ProgressDialog progress2 = U.progress(ctx, GameApp.T("بحث", "Search"), GameApp.T("جاري البحث عن قبائل للمنافسة...", "Searching for competing tribes..."), true);
                new Thread(() -> {
                    JSONObject allTribes = Db.get(TRIBES_PATH.substring(0, TRIBES_PATH.length() - 1));
                    UI.post(() -> {
                        progress2.dismiss();
                        if (allTribes == null) return;
                        final List<String> tNames = new ArrayList<>();
                        final List<String> tKeys = new ArrayList<>();
                        Iterator<String> keys = allTribes.keys();
                        while (keys.hasNext()) {
                            String name = keys.next();
                            if (name.equals(player.clan)) continue;
                            JSONObject data = allTribes.optJSONObject(name);
                            if (data != null) {
                                tNames.add(name + "(Level" + data.optInt("level", 1) + ")");
                                tKeys.add(name);
                            }
                        }
                        if (tNames.isEmpty()) {
                            U.toast(GameApp.T("لا توجد قبائل للمنافسة.", "There are no tribes to compete with."));
                            return;
                        }
                        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                        b.setTitle(GameApp.T("الهجوم على القبائل", "Attack Tribes"));
                        b.setItems(tNames.toArray(new String[0]), (d, idx) -> startTribeWar(tKeys.get(idx)));
                        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                        b.show();
                    });
                }).start();
            });
        }).start();
    }

    private static void startTribeWar(final String targetName) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final ProgressDialog progress = U.progress(ctx, GameApp.T("حرب القبائل", "Tribes War"), GameApp.T("جاري حساب موازين القوى...", "Calculating the balance of power..."), true);
        new Thread(() -> {
            JSONObject myCov = Db.get(TRIBES_PATH + Db.encode(player.clan));
            JSONObject targetCov = Db.get(TRIBES_PATH + Db.encode(targetName));
            UI.post(() -> {
                progress.dismiss();
                if (myCov == null || targetCov == null) return;
                long myPower = (myCov.optInt("level", 1)) * 1000000L + (myCov.optInt("members_count", 1)) * 500000L;
                long targetPower = (targetCov.optInt("level", 1)) * 1000000L + (targetCov.optInt("members_count", 1)) * 500000L;
                JSONObject myGuard = myCov.optJSONObject("guardian");
                JSONObject tgGuard = targetCov.optJSONObject("guardian");
                if (myGuard != null) myPower += (long) (myGuard.optDouble("atk", 0) * 100);
                if (tgGuard != null) targetPower += (long) (tgGuard.optDouble("atk", 0) * 100);
                double winChance = 50 + (myPower - targetPower) / 100000.0;
                winChance = Math.max(10, Math.min(90, winChance));
                boolean isWin = NumberUtil.rand(1, 100) <= winChance;
                String battleLog = isWin
                        ? GameApp.T("• نجح جيشكم في اختراق الحصون.\n• تراجع مدافعو الخصم أمام قوتكم.", "• Your army managed to break through the fortresses.\n• The enemy defenders retreated before your power.")
                        : GameApp.T("• صمدت أسوار الخصم.\n• تراجع جيشكم بعد خسائر فادحة.", "• The enemy walls held firm.\n• Your army retreated after heavy losses.");
                StringBuilder finalMsg = new StringBuilder(String.format(Locale.US,
                        GameApp.T("تقرير الحرب السريع:\n%s ضد %s\n\n%s\n\nقوة هجومكم التقديرية: %d\nقوة دفاعهم التقديرية: %d",
                                "Quick War Report:\n%s vs %s\n\n%s\n\nYour Estimated Attack Power: %d\nTheir Estimated Defense Power: %d"),
                        player.clan, targetName, battleLog, myPower, targetPower));
                try {
                    if (isWin) {
                        long goldStolen = (long) Math.floor(targetCov.optLong("gold", 0) * 0.15);
                        long essenceWon = 1000 + (targetCov.optInt("level", 1)) * 200L;
                        finalMsg.append(String.format(Locale.US,
                                GameApp.T("\n\nالنتيجة: انتصار ساحق!\nذهب مسروق: %d\nجوهر مكتسب: %d\nسمعة: +2000", "\n\nResult: Crushing Victory!\nStolen Gold: %d\nEssence Earned: %d\nReputation: +2000"), goldStolen, essenceWon));
                        myCov.put("essence", myCov.optLong("essence", 0) + essenceWon);
                        myCov.put("rep", myCov.optLong("rep", 0) + 2000);
                        myCov.put("last_attack_time", System.currentTimeMillis() / 1000);
                        myCov.put("gold", myCov.optLong("gold", 0) + goldStolen);
                        targetCov.put("gold", Math.max(0, targetCov.optLong("gold", 0) - goldStolen));
                        targetCov.put("rep", Math.max(0, targetCov.optLong("rep", 0) - 1500));
                        addTribeLog(myCov, GameApp.T("انتصر" + player.name + "في هجوم على قبيلة" + targetName + "وغنمنا" + goldStolen + "ذهب و" + essenceWon + "جوهر.", player.name + "won an attack on the tribe" + targetName + "and we gained" + goldStolen + "gold and" + essenceWon + "essence."));
                        addTribeLog(targetCov, GameApp.T("تعرضت قبيلتنا لهجوم من قبيلة" + player.clan + "بقيادة" + player.name + "وخسرنا" + goldStolen + "ذهب و 1500 سمعة.", "Our tribe was attacked by the tribe" + player.clan + "led by" + player.name + "and we lost" + goldStolen + "gold and 1500 reputation."));
                    } else {
                        finalMsg.append(GameApp.T("\n\nالنتيجة: هزيمة قاسية!\nسمعة: -1000", "\n\nResult: Bitter Defeat!\nReputation: -1000"));
                        myCov.put("rep", Math.max(0, myCov.optLong("rep", 0) - 1000));
                        myCov.put("last_attack_time", System.currentTimeMillis() / 1000);
                        addTribeLog(myCov, GameApp.T("فشل هجوم" + player.name + "على قبيلة" + targetName + "وخسرنا 1000 سمعة.", player.name + "'s attack on the tribe" + targetName + "failed and we lost 1000 reputation."));
                        addTribeLog(targetCov, GameApp.T("تصدى حراسنا بنجاح لهجوم من قبيلة" + player.clan + "بقيادة" + player.name + ".", "Our guards successfully repelled an attack from the tribe" + player.clan + "led by" + player.name + "."));
                    }
                } catch (Exception ignored) {}
                final String result = finalMsg.toString();
                new Thread(() -> {
                    Db.put(TRIBES_PATH + Db.encode(player.clan), myCov);
                    Db.put(TRIBES_PATH + Db.encode(targetName), targetCov);
                    UI.post(() -> U.alert(ctx, GameApp.T("نتائج الحرب", "War Results"), result, GameApp.T("موافق", "OK"), null));
                }).start();
            });
        }).start();
    }

    private static void openTribeMembersUI(final JSONObject cov, final String myRole) {
        final Context ctx = GameApp.uiCtx();
        JSONObject members = cov.optJSONObject("members");
        if (members == null) {
            U.toast(GameApp.T("لا يوجد أعضاء.", "There are no members."));
            return;
        }
        final List<String> mNames = new ArrayList<>();
        final List<String> mIds = new ArrayList<>();
        Iterator<String> keys = members.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            JSONObject data = members.optJSONObject(id);
            if (data != null) {
                String rName = data.optString("role", "member").equals("leader") ? GameApp.T("قائد", "Leader")
                        : (data.optString("role", "member").equals("elder") ? GameApp.T("مشرف", "Moderator") : GameApp.T("عضو", "Member"));
                mNames.add(data.optString("name", id) + "(" + rName + ")");
                mIds.add(id);
            }
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("أعضاء القبيلة", "Tribe Members"));
        b.setItems(mNames.toArray(new String[0]), (d, i) -> {
            final String targetId = mIds.get(i);
            final JSONObject targetData = members.optJSONObject(targetId);
            if (targetData == null || targetId.equals(GameApp.player.username)) return;
            List<String> opts = new ArrayList<>();
            opts.add(GameApp.T("عرض الملف الشخصي", "View Profile"));
            if (myRole.equals("leader")) {
                opts.add(GameApp.T("ترقية لمشرف", "Promote to Moderator"));
                opts.add(GameApp.T("تنزيل لعضو", "Demote to Member"));
                opts.add(GameApp.T("نقل ملكية القبيلة", "Transfer Tribe Ownership"));
                opts.add(GameApp.T("طرد من القبيلة", "Kick from Tribe"));
            } else if (myRole.equals("elder") && targetData.optString("role", "member").equals("member")) {
                opts.add(GameApp.T("طرد من القبيلة", "Kick from Tribe"));
            }
            AlertDialog.Builder d2 = new AlertDialog.Builder(ctx);
            d2.setTitle(targetData.optString("name", "لاعب"));
            d2.setItems(opts.toArray(new String[0]), (d3, i2) -> {
                String choice = opts.get(i2);
                if (choice.equals(GameApp.T("عرض الملف الشخصي", "View Profile"))) {
                    new Thread(() -> {
                        JSONObject pd = Db.get(USER_PATH + Db.encode(targetId));
                        UI.post(() -> {
                            if (pd == null) {
                                U.alert(ctx, null, GameApp.T("تعذر تحميل بيانات اللاعب.", "Failed to load player data."), GameApp.T("حسنا", "OK"), null);
                                return;
                            }
                            JSONObject st = pd.optJSONObject("stats");
                            String profile = String.format(Locale.US,
                                    GameApp.T("الاسم: %s\nالمستوى: %d\nالقوة الإجمالية: %d\nالصحة: %d / %d\nالخبرة: %.2f%%\nالذهب: %d\nالكريستال: %d\nالألماس: %d\nالدفاع: %d\nالرشاقة: %d\nالحظ: %d\nالقبيلة: %s",
                                            "Name: %s\nLevel: %d\nTotal Power: %d\nHealth: %d / %d\nExperience: %.2f%%\nGold: %d\nCrystals: %d\nDiamonds: %d\nDefense: %d\nAgility: %d\nLuck: %d\nTribe: %s"),
                                    pd.optString("name", GameApp.T("غير معروف", "Unknown")), pd.optInt("level", 1), pd.optLong("total_power", 0),
                                    (long) (st != null ? st.optDouble("hp", 0) : 0), (long) (st != null ? st.optDouble("max_hp", 100) : 100),
                                    pd.optDouble("exp", 0), pd.optLong("gold", 0), pd.optLong("crystals", 0),
                                    pd.optLong("diamonds", 0), (long) (st != null ? st.optDouble("endurance", 0) : 0),
                                    (long) (st != null ? st.optDouble("agility", 0) : 0), (long) (st != null ? st.optDouble("luck", 0) : 0),
                                    pd.optString("clan", "لا يوجد"));
                            U.alert(ctx, GameApp.T("الملف الشخصي لـ", "Profile of") + pd.optString("name", GameApp.T("لاعب", "Player")), profile, GameApp.T("موافق", "OK"), null);
                        });
                    }).start();
                } else if (choice.equals(GameApp.T("ترقية لمشرف", "Promote to Moderator"))) {
                    try { targetData.put("role", "elder"); } catch (Exception ignored) {}
                    new Thread(() -> Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov)).start();
                    U.toast(GameApp.T("تمت الترقية!", "Promoted!"));
                } else if (choice.equals(GameApp.T("تنزيل لعضو", "Demote to Member"))) {
                    try { targetData.put("role", "member"); } catch (Exception ignored) {}
                    new Thread(() -> Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov)).start();
                    U.toast(GameApp.T("تم التنزيل!", "Demoted!"));
                } else if (choice.equals(GameApp.T("نقل ملكية القبيلة", "Transfer Tribe Ownership"))) {
                    AlertDialog.Builder d4 = new AlertDialog.Builder(ctx);
                    d4.setView(U.msg(GameApp.T("هل أنت متأكد من نقل الملكية؟ ستفقد صلاحيات القائد.", "Are you sure you want to transfer ownership? You will lose leader permissions.")));
                    d4.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (d5, w) -> {
                        try {
                            JSONObject me = cov.optJSONObject("members").optJSONObject(GameApp.player.username);
                            if (me != null) me.put("role", "elder");
                            targetData.put("role", "leader");
                            cov.put("leader", targetData.optString("name", ""));
                        } catch (Exception ignored) {}
                        new Thread(() -> Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov)).start();
                        U.toast(GameApp.T("تم نقل الملكية!", "Ownership transferred!"));
                    });
                    d4.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    d4.show();
                } else if (choice.equals(GameApp.T("طرد من القبيلة", "Kick from Tribe"))) {
                    if (myRole.equals("elder") && targetData.optString("role", "").equals("leader")) {
                        U.alert(ctx, null, GameApp.T("المشرف لا يمكنه طرد القائد.", "A moderator cannot kick the leader."), GameApp.T("حسنا", "OK"), null);
                        return;
                    }
                    try {
                        if (members != null) {
                            members.remove(targetId);
                            int actualCount = members.length();
                            cov.put("members_count", Math.max(0, actualCount));
                        } else {
                            cov.put("members_count", 0);
                        }
                    } catch (Exception ignored) {}
                    new Thread(() -> {
                        Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov);
                        JSONObject pData = Db.get(USER_PATH + Db.encode(targetId));
                        if (pData != null) {
                            try {
                                pData.put("clan", "لا يوجد");
                                Db.put(USER_PATH + Db.encode(targetId), pData);
                            } catch (Exception ignored) {}
                        }
                    }).start();
                    U.toast(GameApp.T("تم الطرد!", "Kicked!"));
                }
            });
            d2.show();
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void openTribeAdminUI(final JSONObject cov, final String myRole) {
        final Context ctx = GameApp.uiCtx();
        List<String> opts = new ArrayList<>();
        opts.add(GameApp.T("تعديل وصف القبيلة", "Edit Tribe Description"));
        if (myRole.equals("leader")) opts.add(GameApp.T("تطوير مستوى القبيلة", "Upgrade Tribe Level"));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("إدارة القبيلة", "Tribe Management"));
        b.setItems(opts.toArray(new String[0]), (d, i) -> {
            String choice = opts.get(i);
            if (choice.equals(GameApp.T("تعديل وصف القبيلة", "Edit Tribe Description"))) {
                final EditText e = new EditText(ctx);
                e.setText(cov.optString("description", ""));
                AlertDialog.Builder d2 = new AlertDialog.Builder(ctx);
                d2.setTitle(GameApp.T("وصف القبيلة", "Tribe Description"));
                d2.setView(e);
                d2.setPositiveButton(GameApp.T("حفظ", "Save"), (d3, w) -> {
                    try { cov.put("description", e.getText().toString()); } catch (Exception ignored) {}
                    new Thread(() -> Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov)).start();
                    U.toast(GameApp.T("تم الحفظ!", "Saved!"));
                });
                d2.show();
            } else if (choice.equals(GameApp.T("تطوير مستوى القبيلة", "Upgrade Tribe Level"))) {
                checkCovLevelUp(cov);
            }
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void checkCovLevelUp(final JSONObject cov) {
        int exp = cov.optInt("exp", 0);
        int maxExp = cov.optInt("max_exp", 1000);
        if (exp >= maxExp) {
            try {
                cov.put("level", cov.optInt("level", 1) + 1);
                cov.put("exp", exp - maxExp);
                cov.put("max_exp", cov.optInt("level", 1) * 1500);
            } catch (Exception ignored) {}
            new Thread(() -> Db.put(TRIBES_PATH + Db.encode(GameApp.player.clan), cov)).start();
            U.alert(GameApp.uiCtx(), null, GameApp.T("ترقت القبيلة إلى المستوى" + cov.optInt("level", 1) + "!", "The tribe rose to level" + cov.optInt("level", 1) + "!"), GameApp.T("رائع", "Awesome"), null);
        } else {
            U.toast(GameApp.T("الخبرة غير كافية للترقية (" + exp + "/" + maxExp + ")", "Not enough experience to upgrade (" + exp + "/" + maxExp + ")"));
        }
    }

    private static void openRelicAltar(JSONObject cov) {
        final Context ctx = GameApp.uiCtx();
        StringBuilder msg = new StringBuilder(GameApp.T("الآثار الأسطورية تمنح تعزيزات دائمة لجميع الأعضاء.\n\nالآثار الحالية:\n", "Legendary relics grant permanent boosts to all members.\n\nCurrent Relics:\n"));
        JSONArray relics = cov.optJSONArray("relics");
        if (relics == null || relics.length() == 0) {
            msg.append(GameApp.T("- لا توجد آثار حالياً. ابحثوا عنها في المهام التعاونية!", "- There are no relics currently. Find them in cooperative quests!"));
        } else {
            for (int i = 0; i < relics.length(); i++) {
                String rId = relics.optString(i);
                for (Relic r : SACRED_RELICS) {
                    if (r.id.equals(rId)) {
                        msg.append("-").append(r.name).append(":").append(r.desc).append("\n");
                    }
                }
            }
        }
        U.alert(ctx, GameApp.T("ركن الآثار الأسطورية", "Legendary Relics Corner"), msg.toString(), GameApp.T("حسنا", "OK"), null);
    }

    private static void openCooperativeQuests(JSONObject cov) {
        final Context ctx = GameApp.uiCtx();
        String[][] quests = {
                {GameApp.T("هزيمة زعيم العالم", "Defeat the World Boss"), "1", "500"},
                {GameApp.T("جمع ذهب للقبيلة", "Collect Gold for the Tribe"), "5000000", "2000"},
                {GameApp.T("الفوز في حروب القبائل", "Win Tribe Wars"), "10", "3000"},
                {GameApp.T("نشاط الأعضاء (قتل وحوش)", "Member Activity (Kill Monsters)"), "1000", "1500"}
        };
        long[] currents = {
                cov.optLong("quest_progress", 0),
                cov.optLong("total_gold_donated", 0),
                cov.optLong("war_wins", 0),
                cov.optLong("total_kills", 0)
        };
        List<String> qNames = new ArrayList<>();
        for (int i = 0; i < quests.length; i++) {
            qNames.add(quests[i][0] + "(" + currents[i] + "/" + quests[i][1] + ")");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("المهام التعاونية المشتركة", "Shared Cooperative Quests"));
        b.setView(U.msg(GameApp.T("هذه المهام ينفذها جميع أعضاء القبيلة معاً. عند اكتمال أي مهمة، تحصل القبيلة على الجوهر تلقائياً.", "These quests are carried out by all tribe members together. When any quest is completed, the tribe automatically receives the essence.")));
        b.setItems(qNames.toArray(new String[0]), (d, i) -> {
            String q = quests[i][0];
            String target = quests[i][1];
            String reward = quests[i][2];
            U.alert(ctx, q, GameApp.T("الهدف:" + target + "\nالتقدم الحالي:" + currents[i] + "\nالجائزة:" + reward + "جوهر\n\nساهم مع زملائك في القبيلة لإكمال هذه المهمة!", "Goal:" + target + "\nCurrent Progress:" + currents[i] + "\nReward:" + reward + "essence\n\nContribute with your tribe mates to complete this quest!"), GameApp.T("حسناً", "OK"), null);
        });
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        b.show();
    }

    private static void leaveTribe(final JSONObject cov, final String myRole) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        if (myRole.equals("leader")) {
            U.alert(ctx, null, GameApp.T("لا يمكنك مغادرة القبيلة وأنت القائد! انقل الملكية أولاً.", "You cannot leave the tribe while you are the leader! Transfer ownership first."), GameApp.T("حسناً", "OK"), null);
            return;
        }
        new Thread(() -> {
            JSONObject members = cov.optJSONObject("members");
            if (members != null) {
                members.remove(player.username);
                int actualCount = members.length();
                try { cov.put("members_count", Math.max(0, actualCount)); } catch (Exception ignored) {}
            } else {
                try { cov.put("members_count", 0); } catch (Exception ignored) {}
            }
            Db.put(TRIBES_PATH + Db.encode(player.clan), cov);
            player.clan = "لا يوجد";
            SaveSystem.saveAndRefresh();
            UI.post(() -> U.alert(ctx, null, GameApp.T("لقد غادرت القبيلة.", "You have left the tribe."), GameApp.T("حسنا", "OK"), null));
        }).start();
    }

    private static void deleteTribe(final JSONObject cov) {
        final Context ctx = GameApp.uiCtx();
        final PlayerData player = GameApp.player;
        final String clanName = player.clan;
        AlertDialog.Builder conf = new AlertDialog.Builder(ctx);
        conf.setTitle(GameApp.T("تأكيد حذف القبيلة", "Confirm Tribe Deletion"));
        conf.setView(U.msg(GameApp.T("هل أنت متأكد من حذف قبيلة [", "Are you sure you want to delete the tribe [") + clanName + GameApp.T("] نهائياً من السيرفر وقاعدة البيانات وكل مكان؟\n\nسيتم طرد جميع الأعضاء تلقائياً ويصبحون بلا قبيلة. لا يمكن التراجع عن هذا الإجراء!", "] permanently from the server and database everywhere?\n\nAll members will be automatically kicked and become tribe-less. This action cannot be undone!")));
        conf.setPositiveButton(GameApp.T("حذف نهائي", "Delete Permanently"), (d, w) -> {
            final ProgressDialog progress = U.progress(ctx, GameApp.T("حذف القبيلة", "Deleting Tribe"), GameApp.T("جاري حذف القبيلة وطرد الأعضاء...", "Deleting the tribe and kicking members..."), true);
            new Thread(() -> {
                try {
                    JSONObject members = cov.optJSONObject("members");
                    if (members != null) {
                        Iterator<String> keys = members.keys();
                        List<String> memberIds = new ArrayList<>();
                        while (keys.hasNext()) {
                            String id = keys.next();
                            if (!id.equals(player.username)) memberIds.add(id);
                        }
                        for (final String id : memberIds) {
                            try {
                                JSONObject pData = Db.get(USER_PATH + Db.encode(id));
                                if (pData != null) {
                                    pData.put("clan", "لا يوجد");
                                    JSONArray inbox = pData.optJSONArray("inbox");
                                    if (inbox == null) inbox = new JSONArray();
                                    JSONObject note = new JSONObject();
                                    note.put("type", "tribe_deleted");
                                    note.put("tribe_name", clanName);
                                    inbox.put(note);
                                    pData.put("inbox", inbox);
                                    Db.put(USER_PATH + Db.encode(id), pData);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    Db.delete(TRIBES_PATH + Db.encode(clanName));
                    player.clan = "لا يوجد";
                    SaveSystem.saveAndRefresh();
                    UI.post(() -> {
                        progress.dismiss();
                        U.alert(ctx, null, GameApp.T("تم حذف قبيلة [", "Tribe [") + clanName + GameApp.T("] نهائياً وأصبح جميع الأعضاء بلا قبيلة.", "] was deleted permanently and all members are now tribe-less."), GameApp.T("حسناً", "OK"), null);
                    });
                } catch (Exception e) {
                    UI.post(() -> {
                        try { progress.dismiss(); } catch (Exception ignored) {}
                        U.alert(ctx, null, GameApp.T("حدث خطأ أثناء حذف القبيلة.", "An error occurred while deleting the tribe."), GameApp.T("حسناً", "OK"), null);
                    });
                }
            }).start();
        });
        conf.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        conf.show();
    }
}
