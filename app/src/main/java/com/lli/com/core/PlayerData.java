package com.lli.com.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PlayerData {
    public String username = "";
    public String name = "";
    public String gender = "";
    public String language = "";
    public int level = 1;
    public double exp = 0;
    public int maxExp = 100;
    public int points = 10;
    public long gold = 100;
    public long bankGold = 0;
    public long crystals = 0;
    public long diamonds = 0;
    public Stats stats = new Stats();
    public TempStats tempStats = new TempStats();
    public List<ItemData> inventory = new ArrayList<>();
    public List<String> equipped = new ArrayList<>();
    public List<ChestData> lockedChests = new ArrayList<>();
    public String clan = "لا يوجد";
    public int prestigeLevel = 0;
    public String prestigeTitle = null;
    public long totalPower = 0;
    public List<String> news = new ArrayList<>();
    public int towerFloor = 1;
    public Quest currentQuest = new Quest();
    public int dragonLevel = 100;
    public int killsInArea = 0;
    public String location = "المنطقة الآمنة";
    public int steps = 0;
    public int targetSteps = 5;
    public String pet = "لا يوجد";
    public ZombieProgress zombieProgress = null;
    public boolean adminData = false;
    public boolean isDev = false;
    public boolean canManageShop = false;
    public boolean canEditData = false;
    public boolean leaderVisible = true;
    public boolean hideOnline = false;
    public boolean isBanned = false;
    public long bannedUntil = 0;
    public String banReason = null;
    public String banType = null;
    public boolean chatBanned = false;
    public long chatBanUntil = 0;
    public String chatBanReason = null;
    public boolean isJailed = false;
    public long jailUntil = 0;
    public String jailReason = null;
    public String lastDailyReset = "";
    public int dailyInvasionFree = 0;
    public int dailyInvasionPaid = 0;
    public long lastInvasionTime = 0;
    public int chestSlotsExtra = 0;
    public int bagExpansions = 0;
    public String gameVersion = "";
    public Map<String, Boolean> towerMilestoneClaimed = new HashMap<>();
    public boolean socialRewardClaimed = false;
    public long lastOnline = 0;
    public long lastGiftTime = 0;
    public long essence = 0;
    public boolean bossClaimed = false;
    public long lastBuildingReward = 0;
    public int loginStreak = 0;
    public long lastDiamondShopTime = 0;
    public int dailyWheelCount = 0;
    public int dailyMysteryCount = 0;
    public long meditationTime = 0;
    public long pvpHourStart = 0;
    public int pvpMatchesUsed = 0;
    public List<FriendEntry> friendsDetailed = new ArrayList<>();
    public List<String> friends = new ArrayList<>();
    public List<FriendEntry> friendRequests = new ArrayList<>();
    public Map<String, Long> achStats = new HashMap<>();
    public List<String> achClaimed = new ArrayList<>();
    public List<String> battleLog = new ArrayList<>();
    public long autoStart = 0;
    public long autoDuration = 0;
    public long arenaMedals = 0;
    public Map<String, Boolean> notifPrefs = new HashMap<>();
    public List<String> inbox = new ArrayList<>();

    public double getTotalStrength() {
        return stats.strength + tempStats.str;
    }
    public double getTotalEndurance() {
        return stats.endurance + tempStats.endurance;
    }
    public double getTotalAgility() {
        return stats.agility + tempStats.agi;
    }
    public double getTotalLuck() {
        return stats.luck + tempStats.luck;
    }

    /** Effective max HP including a temporary healing-potion bonus while active. */
    public double getTotalMaxHp() {
        double bonus = (tempStats != null && tempStats.active()) ? tempStats.hp : 0;
        return stats.maxHp + bonus;
    }

    /** Current HP clamped to the effective max HP. */
    public double getTotalHp() {
        return Math.min(stats.hp, getTotalMaxHp());
    }

    /** Drops any HP above the effective max (call after a temp bonus expires). */
    public void clampHpToMax() {
        double max = getTotalMaxHp();
        if (stats.hp > max) stats.hp = max;
        if (stats.hp < 0) stats.hp = 0;
    }

    public int bagLimitPerSection() {
        return 6 + bagExpansions * 3;
    }

    public int getBagExpansionCost() {
        return 75 + bagExpansions;
    }

    public static String bagSection(ItemData v) {
        if (v == null) return "أدوات مساعدة";
        if (v.isScroll || (v.name != null && v.name.contains("مخطوطة"))) return "مخطوطات";
        if (v.isPotion || (v.name != null && v.name.contains("جرعة"))) return "جرع";
        if (v.category != null) {
            if (v.category.equals("سلاح")) return "أسلحة";
            if (v.category.equals("دروع")) return "دروع";
            if (v.category.equals("المتجر الملكي") || v.category.equals("ملكي")) return "المتجر الملكي";
            if (v.category.equals("أدوات مساعدة")) return "أدوات مساعدة";
        }
        return "أدوات مساعدة";
    }

    // Removes unequipped items that exceed the per-section limit.
    // The newest items (end of each section group) are removed first.
    public int enforceBagLimits() {
        if (inventory == null || inventory.isEmpty()) return 0;
        int limit = bagLimitPerSection();
        java.util.HashSet<ItemData> keep = new java.util.HashSet<>();
        java.util.HashMap<String, Integer> keptCount = new java.util.HashMap<>();
        for (ItemData it : inventory) {
            if (it == null) continue;
            boolean isEq = equipped != null && equipped.contains(it.name);
            if (isEq) { keep.add(it); continue; }
            String sec = bagSection(it);
            int c = keptCount.containsKey(sec) ? keptCount.get(sec) : 0;
            if (c < limit) {
                keep.add(it);
                keptCount.put(sec, c + 1);
            }
        }
        int removed = inventory.size() - keep.size();
        if (removed > 0) {
            List<ItemData> rebuilt = new ArrayList<>();
            for (ItemData it : inventory) if (it != null && keep.contains(it)) rebuilt.add(it);
            inventory.clear();
            inventory.addAll(rebuilt);
        }
        return removed;
    }

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("username", username);
            o.put("name", name);
            if (gender != null && !gender.isEmpty()) o.put("gender", gender);
            if (language != null && !language.isEmpty()) o.put("language", language);
            o.put("level", level);
            o.put("exp", exp);
            o.put("max_exp", maxExp);
            o.put("points", points);
            o.put("gold", gold);
            o.put("bank_gold", bankGold);
            o.put("crystals", crystals);
            o.put("diamonds", diamonds);
            o.put("bag_expansions", bagExpansions);
            if (gameVersion != null && !gameVersion.isEmpty()) o.put("game_version", gameVersion);
            o.put("stats", stats.toJSON());
            o.put("temp_stats", tempStats.toJSON());
            JSONArray inv = new JSONArray();
            for (ItemData it : inventory) inv.put(it.toJSON());
            o.put("inventory", inv);
            JSONArray eq = new JSONArray();
            for (String s : equipped) eq.put(s);
            o.put("equipped", eq);
            JSONArray chests = new JSONArray();
            for (ChestData c : lockedChests) chests.put(c.toJSON());
            o.put("locked_chests", chests);
            o.put("clan", clan);
            o.put("prestige_level", prestigeLevel);
            if (prestigeTitle != null) o.put("prestige_title", prestigeTitle);
            o.put("total_power", totalPower);
            JSONArray n = new JSONArray();
            for (String s : news) n.put(s);
            o.put("news", n);
            o.put("tower_floor", towerFloor);
            o.put("current_quest", currentQuest.toJSON());
            o.put("dragon_level", dragonLevel);
            o.put("kills_in_area", killsInArea);
            o.put("location", location);
            o.put("steps", steps);
            o.put("target_steps", targetSteps);
            o.put("pet", pet);
            if (zombieProgress != null) o.put("zombie_progress", zombieProgress.toJSON());
            if (adminData) o.put("admin_data", true);
            if (isDev) o.put("is_dev", true);
            if (canManageShop) o.put("can_manage_shop", true);
            if (canEditData) o.put("can_edit_data", true);
            if (!leaderVisible) o.put("leader_visible", false);
            if (hideOnline) o.put("hide_online", true);
            if (isBanned) { o.put("is_banned", true); o.put("banned_until", bannedUntil); if (banReason != null) o.put("ban_reason", banReason); if (banType != null) o.put("ban_type", banType); }
            if (chatBanned) { o.put("chat_banned", true); o.put("chat_ban_until", chatBanUntil); if (chatBanReason != null) o.put("chat_ban_reason", chatBanReason); }
            if (isJailed) { o.put("is_jailed", true); o.put("jail_until", jailUntil); if (jailReason != null) o.put("jail_reason", jailReason); }
            if (!lastDailyReset.isEmpty()) o.put("last_daily_reset", lastDailyReset);
            o.put("daily_invasion_free", dailyInvasionFree);
            o.put("daily_invasion_paid", dailyInvasionPaid);
            o.put("last_invasion_time", lastInvasionTime);
            o.put("chest_slots_extra", chestSlotsExtra);
            JSONObject tmc = new JSONObject();
            for (Map.Entry<String, Boolean> e : towerMilestoneClaimed.entrySet()) tmc.put(e.getKey(), e.getValue());
            o.put("tower_milestone_claimed", tmc);
            if (socialRewardClaimed) o.put("social_reward_claimed", true);
            o.put("last_online", lastOnline);
            o.put("essence", essence);
            if (bossClaimed) o.put("boss_claimed", true);
            o.put("login_streak", loginStreak);
            o.put("last_gift_time", lastGiftTime);
            o.put("last_building_reward", lastBuildingReward);
            o.put("last_diamond_shop_time", lastDiamondShopTime);
            o.put("daily_wheel_count", dailyWheelCount);
            o.put("daily_mystery_count", dailyMysteryCount);
            o.put("meditation_time", meditationTime);
            o.put("pvp_hour_start", pvpHourStart);
            o.put("pvp_matches_used", pvpMatchesUsed);
            JSONArray fd = new JSONArray();
            for (FriendEntry f : friendsDetailed) fd.put(f.toJSON());
            o.put("friends_detailed", fd);
            JSONArray fr = new JSONArray();
            for (String s : friends) fr.put(s);
            o.put("friends", fr);
            JSONArray fq = new JSONArray();
            for (FriendEntry r : friendRequests) fq.put(r.toJSON());
            o.put("friend_requests", fq);
        JSONObject ach = new JSONObject();
            for (Map.Entry<String, Long> e : achStats.entrySet()) ach.put(e.getKey(), e.getValue());
            o.put("ach_stats", ach);
            JSONArray acc = new JSONArray();
            for (String s : achClaimed) acc.put(s);
            o.put("ach_claimed", acc);
            JSONArray blog = new JSONArray();
            for (String s : battleLog) blog.put(s);
            o.put("battle_log", blog);
            o.put("auto_start", autoStart);
            o.put("auto_duration", autoDuration);
            o.put("arena_medals", arenaMedals);
            JSONObject np = new JSONObject();
            for (Map.Entry<String, Boolean> e : notifPrefs.entrySet()) np.put(e.getKey(), e.getValue());
            o.put("notif_prefs", np);
            JSONArray ib = new JSONArray();
            for (String s : inbox) ib.put(s);
            o.put("inbox", ib);
        } catch (JSONException ignored) {}
        return o;
    }

    public static PlayerData fromJSON(JSONObject o) {
        PlayerData p = new PlayerData();
        if (o == null) return p;
        p.username = o.optString("username", "");
        p.name = o.optString("name", "");
        p.gender = o.optString("gender", "");
        p.language = o.optString("language", "");
        p.level = o.optInt("level", 1);
        p.exp = o.optDouble("exp", 0);
        p.maxExp = o.optInt("max_exp", 100);
        p.points = o.optInt("points", 10);
        p.gold = o.optLong("gold", 100);
        p.bankGold = o.optLong("bank_gold", 0);
        p.crystals = o.optLong("crystals", 0);
        p.diamonds = o.optLong("diamonds", 0);
        p.stats = Stats.fromJSON(o.optJSONObject("stats"));
        p.tempStats = TempStats.fromJSON(o.optJSONObject("temp_stats"));
        JSONArray inv = o.optJSONArray("inventory");
        if (inv != null) for (int i = 0; i < inv.length(); i++) p.inventory.add(ItemData.fromJSON(inv.optJSONObject(i)));
        p.bagExpansions = o.optInt("bag_expansions", 0);
        p.gameVersion = o.optString("game_version", "");
        JSONArray eq = o.optJSONArray("equipped");
        if (eq != null) for (int i = 0; i < eq.length(); i++) p.equipped.add(eq.optString(i));
        JSONArray chests = o.optJSONArray("locked_chests");
        if (chests != null) for (int i = 0; i < chests.length(); i++) p.lockedChests.add(ChestData.fromJSON(chests.optJSONObject(i)));
        p.clan = o.optString("clan", "لا يوجد");
        p.prestigeLevel = o.optInt("prestige_level", 0);
        p.prestigeTitle = o.optString("prestige_title", null);
        p.totalPower = o.optLong("total_power", 0);
        JSONArray n = o.optJSONArray("news");
        if (n != null) for (int i = 0; i < n.length(); i++) p.news.add(n.optString(i));
        p.towerFloor = o.optInt("tower_floor", 1);
        p.currentQuest = Quest.fromJSON(o.optJSONObject("current_quest"));
        p.dragonLevel = o.optInt("dragon_level", 100);
        p.killsInArea = o.optInt("kills_in_area", 0);
        p.location = o.optString("location", "المنطقة الآمنة");
        p.steps = o.optInt("steps", 0);
        p.targetSteps = o.optInt("target_steps", 5);
        p.pet = o.optString("pet", "لا يوجد");
        JSONObject zp = o.optJSONObject("zombie_progress");
        if (zp != null) p.zombieProgress = ZombieProgress.fromJSON(zp);
        p.adminData = o.optBoolean("admin_data", false);
        p.isDev = o.optBoolean("is_dev", false);
        p.canManageShop = o.optBoolean("can_manage_shop", false);
        p.canEditData = o.optBoolean("can_edit_data", false);
        p.leaderVisible = o.optBoolean("leader_visible", true);
        p.hideOnline = o.optBoolean("hide_online", false);
        p.isBanned = o.optBoolean("is_banned", false);
        p.bannedUntil = o.optLong("banned_until", 0);
        p.banReason = o.optString("ban_reason", null);
        p.banType = o.optString("ban_type", null);
        p.chatBanned = o.optBoolean("chat_banned", false);
        p.chatBanUntil = o.optLong("chat_ban_until", 0);
        p.chatBanReason = o.optString("chat_ban_reason", null);
        p.isJailed = o.optBoolean("is_jailed", false);
        p.jailUntil = o.optLong("jail_until", 0);
        p.jailReason = o.optString("jail_reason", null);
        p.lastDailyReset = o.optString("last_daily_reset", "");
        p.dailyInvasionFree = o.optInt("daily_invasion_free", 0);
        p.dailyInvasionPaid = o.optInt("daily_invasion_paid", 0);
        p.lastInvasionTime = o.optLong("last_invasion_time", 0);
        p.chestSlotsExtra = o.optInt("chest_slots_extra", 0);
        JSONObject tmc = o.optJSONObject("tower_milestone_claimed");
        if (tmc != null) {
            Iterator<String> it = tmc.keys();
            while (it.hasNext()) {
                String k = it.next();
                p.towerMilestoneClaimed.put(k, tmc.optBoolean(k, false));
            }
        }
        p.socialRewardClaimed = o.optBoolean("social_reward_claimed", false);
        p.lastOnline = o.optLong("last_online", 0);
        p.essence = o.optLong("essence", 0);
        p.bossClaimed = o.optBoolean("boss_claimed", false);
        p.loginStreak = o.optInt("login_streak", 0);
        p.lastGiftTime = o.optLong("last_gift_time", 0);
        p.lastBuildingReward = o.optLong("last_building_reward", 0);
        p.lastDiamondShopTime = o.optLong("last_diamond_shop_time", 0);
        p.dailyWheelCount = o.optInt("daily_wheel_count", 0);
        p.dailyMysteryCount = o.optInt("daily_mystery_count", 0);
        p.meditationTime = o.optLong("meditation_time", 0);
        p.pvpHourStart = o.optLong("pvp_hour_start", 0);
        p.pvpMatchesUsed = o.optInt("pvp_matches_used", 0);
        JSONArray fd = o.optJSONArray("friends_detailed");
        if (fd != null) for (int i = 0; i < fd.length(); i++) p.friendsDetailed.add(FriendEntry.fromJSON(fd.optJSONObject(i)));
        JSONArray fr = o.optJSONArray("friends");
        if (fr != null) for (int i = 0; i < fr.length(); i++) p.friends.add(fr.optString(i));
        JSONArray fq = o.optJSONArray("friend_requests");
        if (fq != null) for (int i = 0; i < fq.length(); i++) p.friendRequests.add(FriendEntry.fromJSON(fq.optJSONObject(i)));
        JSONObject ach = o.optJSONObject("ach_stats");
        if (ach != null) {
            Iterator<String> it = ach.keys();
            while (it.hasNext()) {
                String k = it.next();
                p.achStats.put(k, ach.optLong(k, 0));
            }
        }
        JSONArray acc = o.optJSONArray("ach_claimed");
        if (acc != null) for (int i = 0; i < acc.length(); i++) p.achClaimed.add(acc.optString(i));
        JSONArray blog = o.optJSONArray("battle_log");
        if (blog != null) for (int i = 0; i < blog.length(); i++) p.battleLog.add(blog.optString(i));
        p.autoStart = o.optLong("auto_start", 0);
        p.autoDuration = o.optLong("auto_duration", 0);
        p.arenaMedals = o.optLong("arena_medals", 0);
        JSONObject np = o.optJSONObject("notif_prefs");
        if (np != null) {
            Iterator<String> it = np.keys();
            while (it.hasNext()) {
                String k = it.next();
                p.notifPrefs.put(k, np.optBoolean(k, true));
            }
        }
        JSONArray ib = o.optJSONArray("inbox");
        if (ib != null) for (int i = 0; i < ib.length(); i++) p.inbox.add(ib.optString(i));
        try { p.enforceBagLimits(); } catch (Throwable ignored) {}
        applySpecialPermissions(p);
        return p;
    }

    public static void applySpecialPermissions(PlayerData p) {
        if (p != null && p.username != null && com.lli.com.GameApp.isDevUsername(p.username)) {
            p.adminData = true;
            p.isDev = true;
        }
    }
}
