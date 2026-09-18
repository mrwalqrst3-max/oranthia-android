package com.lli.com.ui;

import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.core.PlayerData;
import com.lli.com.core.PrestigeSystem;

public class GameNav {
    public static TextView infoText;
    public static int currentTab = 1;

    public static void updateInfo() {
        if (infoText == null || GameApp.player == null) return;
        PlayerData p = GameApp.player;
        String pTitle = p.prestigeTitle == null ? "" : "[" + p.prestigeTitle + "]";
        String pDisplay = p.prestigeTitle == null ? p.name : "[" + p.prestigeTitle + "]" + p.name;
        int hpPct = (int) Math.floor(((p.getTotalHp()) / Math.max(p.getTotalMaxHp(), 1)) * 100);
        int expPct = (int) Math.floor(p.exp);
        String s = "" + pDisplay + "| Lv." + p.level + "|" + com.lli.com.core.NumberUtil.formatNumber(p.totalPower)
                + "\n" + GameApp.T("صحة:", "Health:") + hpPct + GameApp.T("% | خبرة:", "% | XP:") + expPct + "%"
                + "\n" + com.lli.com.core.NumberUtil.formatNumber(p.gold) + GameApp.T("ذهب", "gold") + "|" + (long) p.crystals + GameApp.T("كريستال", "crystals") + "|" + p.diamonds + GameApp.T("ألماس", "diamonds")
                + "\n" + GameApp.T("دفاع:", "Defense:") + (long) p.stats.endurance + GameApp.T("| رشاقة:", "| Agility:") + (long) p.stats.agility + GameApp.T("| حظ:", "| Luck:") + (long) p.stats.luck
                + "\n" + GameApp.T("القوة:", "Power:") + com.lli.com.core.NumberUtil.formatNumber(p.totalPower) + GameApp.T("| القبيلة:", "| Clan:") + p.clan;
        infoText.setText(s);
    }

    public static void openPrestigeUI() {
        PrestigeSystem.openPrestigeUI();
    }

    public static void stub(String name) {
        U.alert(GameApp.uiCtx(), name, GameApp.T("هذا النظام قيد التطوير...", "This system is under development..."), GameApp.T("حسنا", "OK"), null);
    }
}
