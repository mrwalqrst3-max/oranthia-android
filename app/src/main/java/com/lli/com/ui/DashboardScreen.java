package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.core.Achievements;
import com.lli.com.core.BattleLog;
import com.lli.com.core.Db;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.SaveSystem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DashboardScreen implements Screen {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private Context ctx;
    private LinearLayout contentFrame;
    private LinearLayout tabBar;
    private String[] tabNames;
    private int adminTabIndex = -1;

    public DashboardScreen(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public View build() {
        LinearLayout mainLay = U.linear(ctx, true);
        tabBar = U.linear(ctx, false);
        java.util.List<String> tabs = new java.util.ArrayList<>();
        tabs.add(GameApp.T("الشخصية", "Character"));
        tabs.add(GameApp.T("المدينة", "City"));
        tabs.add(GameApp.T("المغامرة", "Adventure"));
        tabs.add(GameApp.T("المجتمع", "Community"));
        tabs.add(GameApp.T("العالم", "World"));
        if (GameApp.player.adminData || GameApp.player.isDev || GameApp.player.canManageShop || GameApp.player.canEditData) {
            adminTabIndex = tabs.size();
            tabs.add(GameApp.T("الإدارة", "Admin"));
        }
        tabNames = tabs.toArray(new String[0]);

        final ScrollView contentScroll = new ScrollView(ctx);
        contentScroll.setBackgroundColor(U.BLACK);
        contentFrame = U.linear(ctx, true);
        contentScroll.addView(contentFrame);

        mainLay.addView(tabBar);
        mainLay.addView(contentScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        for (int i = 0; i < tabNames.length; i++) {
            final int idx = i;
            Button b = U.btn(ctx, tabNames[i]);
            b.setLayoutParams(U.lp1(LinearLayout.LayoutParams.WRAP_CONTENT));
            b.setOnClickListener(v -> {
                GameApp.sound.playSnd("click.mp3");
                showTab(idx);
            });
            tabBar.addView(b);
        }
        SaveSystem.updatePower();
        showTab(0);
        return mainLay;
    }

    private void addB(String t, final Runnable f) {
        Button b = U.btn(ctx, t);
        b.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            f.run();
        });
        contentFrame.addView(b, U.lpMargins(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10, 5, 10, 5));
    }

    private void showTab(int idx) {
        GameNav.currentTab = idx + 1;
        contentFrame.removeAllViews();
        // Zone ambience follows the player: plays as soon as the tab opens.
        if (idx == adminTabIndex) {
            GameApp.sound.setAmbient("amb17.mp3");
        } else if (idx == 0) {
            GameApp.sound.setAmbient("amb_character.mp3");
        } else if (idx == 1) {
            GameApp.sound.setAmbient("amb_city.mp3");
        } else if (idx == 2) {
            GameApp.sound.setAmbient("amb_wolves.mp3");
        } else if (idx == 3) {
            GameApp.sound.setAmbient("amb_community.mp3");
        } else if (idx == 4) {
            GameApp.sound.setAmbient("amb_city.mp3");
        }
        boolean dark = (idx == 2 || idx == adminTabIndex);
        ArtView art = new ArtView(ctx, dark,
                tabNames[idx] + GameApp.T("- رسمة متحركة لمشهد اللعبة", "- animated game scene"),
                GameApp.T("لوحة رئيسية. هناك رسمة متحركة تعرض مشهد اللعبة.", "Main panel. There is an animated graphic showing the game scene."), 100);
        contentFrame.addView(art);
        if (idx == adminTabIndex) {
            addB(GameApp.T("لوحة تحكم الإدارة", "Admin Control Panel"), () -> AdminSystem.openAdminPanelUI());
        } else if (idx == 0) {
            buildPersonalTab();
        } else if (idx == 1) {
            buildCityTab();
        } else if (idx == 2) {
            buildAdventureTab();
        } else if (idx == 3) {
            buildCommunityTab();
        } else if (idx == 4) {
            buildWorldTab();
        }
    }

    private void buildPersonalTab() {
        addB(GameApp.T("الإحصائيات والتطوير 🎮", "Stats & Upgrades 🎮"), () -> showStatsDialog());
        addB(GameApp.T("أخبار الشخصية", "Character News"), () -> Dialogs.openPlayerNewsUI());
        PlayerData p = GameApp.player;
        addB(GameApp.T("إعادة توزيع النقاط (5 ألماس)", "Redistribute Points (5 diamonds)"), () -> Dialogs.redistributePoints());
        addB(GameApp.T("الرتب الملكية 👑", "Royal Ranks 👑"), () -> GameNav.openPrestigeUI());
        addB(GameApp.T("الحقيبة والمعدات", "Bag & Equipment"), () -> InventoryUI.openInventoryUI());
        addB(GameApp.T("الإعدادات", "Settings"), () -> SettingsSystem.openSettingsUI());
    }

    private void showStatsDialog() {
        final PlayerData p = GameApp.player;
        final String pFullTitle = p.prestigeTitle != null ? ("[" + p.prestigeTitle + "]") : "";
        final AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(pFullTitle + GameApp.T("إحصائياتك وتطوير البطل", "Your Stats & Hero Upgrades"));
        b.setPositiveButton(GameApp.T("توزيع النقاط (", "Distribute Points (") + p.points + ")", (d, w) -> Dialogs.openPointsUI());
        b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
        final ProgressDialog progress = U.progress(ctx, GameApp.T("الإحصائيات", "Stats"), GameApp.T("جاري تحميل البيانات...", "Loading data..."), true);
        new Thread(() -> {
            String tribeSection = "";
            if (p.clan != null && !p.clan.isEmpty() && !p.clan.equals("لا يوجد")) {
                JSONObject cov = Db.get("tribes_system/" + Db.encode(p.clan));
                if (cov != null) {
                    int tLvl = cov.optInt("level", 1);
                    long tGold = cov.optLong("gold", 0);
                    long tCrys = cov.optLong("tribe_crystals", 0);
                    long tDiam = cov.optLong("tribe_diamonds", 0);
                    int tCount = cov.optInt("members_count", 0);
                    String tLeader = cov.optString("leader", "");
                    tribeSection = GameApp.T("── القبيلة ──\n", "── Tribe ──\n")
                            + GameApp.T("الاسم:", "Name:") + p.clan + GameApp.T("(المستوى", "(Level") + tLvl + ")\n"
                            + GameApp.T("القائد:", "Leader:") + tLeader + "\n"
                            + GameApp.T("الأعضاء:", "Members:") + tCount + "\n"
                            + GameApp.T("الذهب:", "Gold:") + NumberUtil.formatNumber(tGold) + "\n"
                            + GameApp.T("الألماس:", "Diamonds:") + NumberUtil.formatNumber(tDiam) + "\n"
                            + GameApp.T("الكريستال:", "Crystals:") + NumberUtil.formatNumber(tCrys) + "\n";
                } else {
                    tribeSection = GameApp.T("── القبيلة ──\n", "── Tribe ──\n")
                            + GameApp.T("الاسم:", "Name:") + (p.clan.equals("لا يوجد") ? GameApp.T("لا يوجد", "None") : p.clan) + "\n";
                }
            }
            final String fTribe = tribeSection;
            long arenaMedals = 0;
            try {
                JSONObject rec = Db.get("medals/" + Db.encode(p.username));
                arenaMedals = rec == null ? 0 : rec.optLong("count", 0);
            } catch (Exception ignored) {}
            final long fMedals = arenaMedals;
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                double totalStr = p.getTotalStrength();
                double totalDef = p.getTotalEndurance();
                double totalAgi = p.getTotalAgility();
                double totalLck = p.getTotalLuck();
                double totalHp = p.getTotalMaxHp();
                double curHp = p.getTotalHp();
                int hpPct = (int) Math.floor((curHp / totalHp) * 100);
                int expPct = (int) Math.floor(p.exp);
                int exDmg = (int) Math.floor(totalStr * 1.5);
                String msg = GameApp.T("═══ إحصائياتك التفصيلية ═══\n"
                        + "الاسم:" + p.name + "(المستوى " + p.level + ")\n"
                        + "الذهب:" + NumberUtil.formatNumber(p.gold) + "\n"
                        + "الألماس:" + NumberUtil.formatNumber(p.diamonds) + "\n"
                        + "الكريستال:" + NumberUtil.formatNumber(p.crystals) + "\n"
                        + fTribe
                        + "───────────────\n"
                        + "القوة الإجمالية:" + p.totalPower + "\n"
                        + "هجوم:" + (int) Math.floor(totalStr) + "\n"
                        + "دفاع:" + (int) Math.floor(totalDef) + "\n"
                        + "رشاقة:" + (int) Math.floor(totalAgi) + "\n"
                        + "حظ:" + (int) Math.floor(totalLck) + "\n"
                        + "صحة:" + (int) Math.floor(curHp) + "/" + (int) totalHp + "(" + hpPct + "%)\n"
                        + "ضرر متوقع:" + exDmg + "\n"
                        + "الخبرة:" + expPct + "%\n"
                        + "نقاط التطوير:" + p.points + GameApp.T("نقطة", "points") + "\n"
                        + "ميداليات الساحة:" + fMedals + "\n"
                        + "═══════════════════",
                        "=== Your Detailed Stats ===\n"
                        + "Name:" + p.name + "(Level " + p.level + ")\n"
                        + "Gold:" + NumberUtil.formatNumber(p.gold) + "\n"
                        + "Diamonds:" + NumberUtil.formatNumber(p.diamonds) + "\n"
                        + "Crystals:" + NumberUtil.formatNumber(p.crystals) + "\n"
                        + fTribe
                        + "───────────────\n"
                        + "Total Power:" + p.totalPower + "\n"
                        + "Attack:" + (int) Math.floor(totalStr) + "\n"
                        + "Defense:" + (int) Math.floor(totalDef) + "\n"
                        + "Agility:" + (int) Math.floor(totalAgi) + "\n"
                        + "Luck:" + (int) Math.floor(totalLck) + "\n"
                        + "HP:" + (int) Math.floor(curHp) + "/" + (int) totalHp + "(" + hpPct + "%)\n"
                        + "Expected Damage:" + exDmg + "\n"
                        + "XP:" + expPct + "%\n"
                        + "Upgrade Points:" + p.points + "\n"
                        + "Arena Medals:" + fMedals + "\n");
                b.setView(U.msg(msg));
                if (U.uiReady()) b.show();
            });
        }).start();
    }

    private void buildCityTab() {
        addB(GameApp.T("الهدية اليومية وأرباح البنك", "Daily Gift & Bank Profits"), () -> Dialogs.dailyGift());
        addB(GameApp.T("ساحة التأمل (AFK)", "Meditation Square (AFK)"), () -> Dialogs.meditation());
        addB(GameApp.T("المزاد 🏷️", "Auction 🏷️"), () -> Dialogs.auction());
        addB(GameApp.T("المتجر 🏪", "Shop 🏪"), () -> UnifiedShop.openUnifiedShop());
        addB(GameApp.T("متجر الطاقة ⚡", "Energy Shop ⚡"), () -> Dialogs.diamondShop());
        addB(GameApp.T("البنك 🏦", "Bank 🏦"), () -> Dialogs.bank());
        addB(GameApp.T("مانح الطاقة العشوائي 🎲✨", "Random Energy Grant 🎲✨"), () -> Dialogs.energySpring());
        addB(GameApp.T("عجلة الحظ 🎡", "Wheel of Fortune 🎡"), () -> Dialogs.luckWheel());
        addB(GameApp.T("سوق اللاعبين", "Player Market"), () -> MarketSystem.openMarketUI());
        addB(GameApp.T("صندوق الغموض", "Mystery Box"), () -> Dialogs.mysteryBox());
    }

    private void buildAdventureTab() {
        PlayerData p = GameApp.player;
        addB(GameApp.T("الإنجازات", "Achievements"), () -> Achievements.openUI());
        addB(GameApp.T("الاستكشاف التلقائي (AFK)", "Auto-Exploration (AFK)"), () -> AutoExploreSystem.openUI());
        addB(GameApp.T("سجل المعارك", "Battle Log"), () -> BattleLog.openUI());
        addB(GameApp.T("الصناديق 📦", "Chests 📦"), () -> ChestSystem.openFoundChestsUI());
        addB(GameApp.T("البرج 🏰", "Tower 🏰"), () -> ExplorationSystem.openTowerExplorationUI());
        addB(GameApp.T("عالم الزومبي", "Zombie World"), () -> ZombieWorldSystem.enterZombieWorld());
        if (!p.location.equals("المنطقة الآمنة")) {
            addB(GameApp.T("استكشاف المنطقة الحالية (", "Explore Current Zone (") + Dialogs.zoneName(p.location) + ")", () -> ExplorationSystem.openExplorationUI());
        }
        addB(GameApp.T("السفر إلى منطقة جديدة (27 ذهب)", "Travel to a New Zone (27 gold)"), () -> Dialogs.travel());
        addB(GameApp.T("الزعيم العالمي", "World Boss"), () -> WorldBossSystem.openWorldBossUI());
        addB(GameApp.T("المبارزة الاستراتيجية (وحوش / لاعبين)", "Strategic Duel (Monsters / Players)"), () -> Dialogs.strategicDuelMenu());
        addB(GameApp.T("نقابة المرتزقة (المهام)", "Mercenary Guild (Quests)"), () -> Dialogs.mercenaryGuild());
    }

    private void buildCommunityTab() {
        addB(GameApp.T("الدردشة والرسائل", "Chat & Messages"), () -> ChatSystem.openChatSelector());
        addB(GameApp.T("اللاعبون المتصلون", "Online Players"), () -> openOnlinePlayersUI());
        addB(GameApp.T("قائمة الصدارة", "Leaderboard"), () -> Dialogs.leaderboard());
        addB(GameApp.T("قائمة المسؤولين (متصل / غير متصل) 🛡", "Admins List (Online/Offline) 🛡"), () -> openOnlineStaffUI());
        addB(GameApp.T("الأصدقاء 🤝", "Friends 🤝"), () -> FriendsSystem.openFriendsUI());
        addB(GameApp.T("القبيلة", "Tribe"), () -> TribeSystem.openTribeUI());
        addB(GameApp.T("ساحة المبارزة أونلاين", "Online Duel Arena"), () -> StrategicDuelSystem.openOnlineDuelUI());
        addB(GameApp.T("الساحات ⚔️", "Arenas ⚔️"), () -> ArenaSystem.openArenasMenu());
    }

    private void buildWorldTab() {
        addB(GameApp.T("دخول عالم أترايثيا 🌍 (يتطلب المستوى 25)", "Enter Oranthia World 🌍 (requires level 25)"), () -> OranthiaWorldSystem.openWorldUI());
        addB(GameApp.T("وضعية البوابة", "Gate Status"), () -> OranthiaWorldSystem.openWorldUI());
    }

    private void openOnlineStaffUI() {
        final ProgressDialog progress = U.progress(ctx, GameApp.T("قائمة المسؤولين المتصلين", "Connected Admins"), GameApp.T("جاري تحميل...", "Loading..."), true);
        new Thread(() -> {
            JSONObject all = com.lli.com.core.Db.get("players");
            final JSONObject fAll = all;
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                if (fAll == null) {
                    U.alert(ctx, null, GameApp.T("تعذر تحميل القائمة", "Could not load the list"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final List<String[]> staff = new ArrayList<>();
                Iterator<String> it = fAll.keys();
                long now = System.currentTimeMillis() / 1000;
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject pd = fAll.optJSONObject(id);
                    if (pd == null) continue;
                    boolean isStaff = pd.optBoolean("is_dev", false) || pd.optBoolean("admin_data", false) || pd.optBoolean("can_manage_shop", false) || pd.optBoolean("can_edit_data", false);
                    if (!isStaff) continue;
                    if (pd.optBoolean("hide_online", false)) continue;
                    long lastOnline = pd.optLong("last_online", 0);
                    String name = GameApp.T("[مطور] ", "[Developer] ") + pd.optString("name", id);
                    if (pd.optBoolean("admin_data", false)) name = GameApp.T("[مدير] ", "[Admin] ") + pd.optString("name", id);
                    else if (pd.optBoolean("can_manage_shop", false)) name = GameApp.T("[مدير متجر] ", "[Shop Manager] ") + pd.optString("name", id);
                    else if (pd.optBoolean("can_edit_data", false)) name = GameApp.T("[مدير بيانات] ", "[Data Manager] ") + pd.optString("name", id);
                    if (lastOnline > 0 && now - lastOnline <= 120) {
                        staff.add(new String[]{name + " • " + GameApp.T("متصل", "Online"), id});
                    } else {
                        staff.add(new String[]{name + " • " + GameApp.T("غير متصل", "Offline"), id});
                    }
                }
                staff.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
                List<String> names = new ArrayList<>();
                for (String[] p : staff) names.add(p[0]);
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("المسؤولون (متصل / غير متصل) (" + staff.size() + ")", "Admins (Online/Offline) (" + staff.size() + ")"));
                b.setItems(names.toArray(new String[0]), (d, idx) -> {
                    String[] p = staff.get(idx);
                    String[] opts = {
                            GameApp.T("عرض الملف الشخصي", "View Profile")
                    };
                    AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                    b2.setTitle(p[0]);
                    b2.setItems(opts, (d2, i2) -> ChatSystem.openPlayerProfile(p[1]));
                    b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    b2.show();
                });
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                if (U.uiReady()) b.show();
            });
        }).start();
    }

    private void openOnlinePlayersUI() {
        final ProgressDialog progress = U.progress(ctx, GameApp.T("اللاعبون المتصلون", "Online Players"), GameApp.T("جاري تحميل قائمة اللاعبين...", "Loading the players list..."), true);
        new Thread(() -> {
            JSONObject all = com.lli.com.core.Db.get("players");
            final JSONObject fAll = all;
            UI.post(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                if (fAll == null) {
                    U.alert(ctx, null, GameApp.T("لا يوجد لاعبين في قاعدة البيانات", "There are no players in the database"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                final List<String[]> online = new ArrayList<>();
                Iterator<String> it = fAll.keys();
                long now = System.currentTimeMillis() / 1000;
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject pd = fAll.optJSONObject(id);
                    if (pd == null) continue;
                    if (pd.optBoolean("is_banned", false) && pd.optLong("banned_until", 0) > now) continue;
                    if (pd.optBoolean("is_dev", false)) continue;
                    if (pd.optBoolean("admin_data", false) || pd.optBoolean("can_manage_shop", false) || pd.optBoolean("can_edit_data", false)) continue;
                    if (pd.optBoolean("hide_online", false)) continue;
                    long lastOnline = pd.optLong("last_online", 0);
                    if (lastOnline > 0 && now - lastOnline <= 120) {
                        String name = pd.optString("name", id);
                        online.add(new String[]{name, id});
                    }
                }
                if (online.isEmpty()) {
                    U.alert(ctx, null, GameApp.T("لا يوجد لاعبون متصلون حالياً", "No players are currently online"), GameApp.T("حسنا", "OK"), null);
                    return;
                }
                online.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
                List<String> names = new ArrayList<>();
                for (String[] p : online) {
                    names.add("" + p[0] + "•" + GameApp.T("متصل", "Online"));
                }
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("اللاعبون المتصلون (" + online.size() + ")", "Online Players (" + online.size() + ")"));
                b.setItems(names.toArray(new String[0]), (d, idx) -> {
                    String[] p = online.get(idx);
                    String[] opts = {
                            GameApp.T("إرسال رسالة خاصة", "Send Private Message"),
                            GameApp.T("عرض الملف الشخصي", "View Profile")
                    };
                    AlertDialog.Builder b2 = new AlertDialog.Builder(ctx);
                    b2.setTitle(p[0]);
                    b2.setItems(opts, (d2, i2) -> {
                        if (i2 == 0) {
                            ChatSystem.openChat(false, p[1], p[0]);
                        } else {
                            ChatSystem.openPlayerProfile(p[1]);
                        }
                    });
                    b2.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
                    b2.show();
                });
                b.setNegativeButton(GameApp.T("إغلاق", "Close"), null);
                if (U.uiReady()) b.show();
            });
        }).start();
    }
}
