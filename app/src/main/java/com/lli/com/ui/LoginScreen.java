package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.lli.com.GameApp;
import com.lli.com.MainActivity;
import com.lli.com.core.Db;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.PlayerSystem;
import com.lli.com.core.SaveSystem;
import com.lli.com.core.ServerStatus;

import org.json.JSONObject;

public class LoginScreen implements Screen {
    private Context ctx;
    public static boolean skipAutoLogin = false;

    public LoginScreen(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public View build() {
        String savedId = GameApp.prefs.getString("saved_id", "");
        if (!skipAutoLogin && savedId != null && !savedId.isEmpty()) {
            restoreAccount(savedId, false);
            LinearLayout lay = U.linear(ctx, true);
            lay.setPadding(50, 50, 50, 50);
            lay.setGravity(android.view.Gravity.CENTER);
            lay.addView(U.text(ctx, GameApp.T("جاري الاتصال بالسيرفر...", "Connecting to server..."), 20, U.GOLD, true));
            return lay;
        }
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(50, 50, 50, 50);
        lay.setGravity(android.view.Gravity.CENTER);

        TextView title = U.text(ctx, GameApp.T("أترايثيا — عالم الأساطير", "Oranthia — World of Legends"), 30, U.GOLD, true);
        EditText edName = U.edit(ctx, GameApp.T("أدخل اسم بطلك للبدء...", "Enter your hero's name to start..."));
        Button btnNew = U.btn(ctx, GameApp.T("بدء رحلة جديدة", "Start a New Journey"));
        TextView txtOr = U.text(ctx, GameApp.T("--- أو ---", "--- or ---"), 18, U.WHITE, false);
        txtOr.setPadding(0, 40, 0, 40);
        EditText edId = U.edit(ctx, GameApp.T("أدخل الـ ID لاستعادة حسابك...", "Enter your ID to restore your account..."));
        Button btnRestore = U.btn(ctx, GameApp.T("استعادة الحساب", "Restore Account"));

        btnNew.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            String n = edName.getText().toString().trim();
            if (n.isEmpty()) {
                U.toast(GameApp.T("اكتب اسمك أولاً!", "Type your name first!"));
                return;
            }
            showNewPlayerWarning(n);
        });

        btnRestore.setOnClickListener(v -> {
            GameApp.sound.playSnd("click.mp3");
            String pid = edId.getText().toString().trim();
            if (pid.isEmpty()) {
                U.toast(GameApp.T("اكتب الـ ID أولاً!", "Type your ID first!"));
                return;
            }
            showRestoreWarning(pid);
        });

        lay.addView(title);
        lay.addView(edName);
        lay.addView(btnNew);
        lay.addView(txtOr);
        lay.addView(edId);
        lay.addView(btnRestore);
        return lay;
    }

    private void showNewPlayerWarning(String n) {
        LinearLayout warnLay = U.linear(ctx, true);
        warnLay.setPadding(40, 40, 40, 40);
        TextView warnTxt = U.text(ctx,
                GameApp.T("تحذير أمني هام جداً!\n\nنحن كإدارة اللعبة لسنا مسؤولين عن \"المعرف الخاص\" (ID) الخاص بحسابك.\n" +
                        "هذا الـ ID هو مفتاح دخولك الوحيد. إذا قمت بإعطائه لأي شخص آخر، فسيتمكن من الدخول إلى حسابك والتحكم به بالكامل (سرقة الذهب، حذف المعدات، إلخ).\n\n" +
                        "يرجى الحفاظ على سرية الـ ID الخاص بك وعدم مشاركته مع أي شخص مهما ادعى أنه من الإدارة.",
                        "Very Important Security Warning!\n\nAs the game staff, we are NOT responsible for your account's private ID.\n" +
                        "This ID is your only key to your account. If you give it to anyone else, they will be able to log into your account and fully control it (stealing gold, deleting equipment, etc.).\n\n" +
                        "Please keep your ID secret and never share it with anyone, no matter who claims to be from the staff."),
                16, U.GOLD, false);
        CheckBox chk = new CheckBox(ctx);
        chk.setText(GameApp.T("لقد فهمت هذا التحذير وأتحمل مسؤولية حسابي", "I understand this warning and take full responsibility for my account"));
        chk.setTextColor(Color.WHITE);
        warnLay.addView(warnTxt);

        warnLay.addView(U.text(ctx, GameApp.T("اختر جنس بطلك:", "Choose your hero's gender:"), 15, U.GOLD, true));
        final RadioGroup genderGroup = new RadioGroup(ctx);
        genderGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbMale = new RadioButton(ctx);
        rbMale.setText(GameApp.T("ذكر", "Male"));
        rbMale.setTextColor(Color.WHITE);
        rbMale.setId(1001);
        final RadioButton rbFemale = new RadioButton(ctx);
        rbFemale.setText(GameApp.T("أنثى", "Female"));
        rbFemale.setTextColor(Color.WHITE);
        rbFemale.setId(1002);
        genderGroup.addView(rbMale);
        genderGroup.addView(rbFemale);
        warnLay.addView(genderGroup);
        warnLay.addView(chk);

        AlertDialog.Builder w = new AlertDialog.Builder(ctx);
        w.setTitle(GameApp.T("اتفاقية الأمان", "Security Agreement"));
        w.setView(warnLay);
        w.setCancelable(false);
        w.setPositiveButton(GameApp.T("متابعة", "Continue"), (d, i) -> {
            PlayerSystem.initNewPlayer();
            GameApp.player.name = n;
            GameApp.player.gender = genderGroup.getCheckedRadioButtonId() == 1002 ? "female" : "male";
            GameApp.player.language = GameApp.isEn ? "en" : "ar";
            GameApp.player.username = "User_" + NumberUtil.rand(1000, 999999);
            if (n.contains("Legend")) {
                GameApp.player.diamonds += 10;
                GameApp.player.gold += 50000;
                U.toast(GameApp.T("بداية أسطورية! حصلت على 10 ألماس و50 ألف ذهب.", "Legendary start! You got 10 diamonds and 50,000 gold."));
            }
            if (n.equals("KingDragon")) {
                GameApp.player.isDev = true;
                GameApp.player.level = 999;
                GameApp.player.stats.strength = 50000;
                GameApp.player.gold = 9999999;
                GameApp.player.points = 999;
            }
            if (n.equals(GameApp.MASTER_CODE)) {
                GameApp.player.adminData = true;
                GameApp.player.isDev = true;
                U.toast(GameApp.T("تم تفعيل صلاحيات المشرف والمطور لهذا الحساب!", "Admin and developer permissions enabled for this account!"));
            }
            GameApp.prefs.edit().putString("saved_id", GameApp.player.username).apply();
            SaveSystem.save();
            SaveSystem.pushCloudAsync();
            skipAutoLogin = false;
            openMain();
        });
        w.setNegativeButton(GameApp.T("رجوع", "Back"), null);
        AlertDialog dialog = w.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        final Runnable upd = () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setEnabled(chk.isChecked() && genderGroup.getCheckedRadioButtonId() != -1);
        chk.setOnCheckedChangeListener((v, checked) -> upd.run());
        genderGroup.setOnCheckedChangeListener((grp, id) -> upd.run());
    }

    private void showRestoreWarning(String pid) {
        LinearLayout warnLay = U.linear(ctx, true);
        warnLay.setPadding(40, 40, 40, 40);
        TextView warnTxt = U.text(ctx,
                GameApp.T("اتفاقية الأمان واستعادة الحساب!\n\nتنبيه: أنت الآن تحاول الدخول إلى حساب باستخدام الـ ID.\n" +
                        "1. الحفاظ على سرية الـ ID هي مسؤوليتك الشخصية بالكامل.\n" +
                        "2. لا تشارك الـ ID مع أي شخص لتجنب سرقة ممتلكاتك في اللعبة.\n" +
                        "3. الإدارة لن تطلب منك الـ ID الخاص بك أبداً داخل اللعبة.\n\n" +
                        "هل أنت متأكد أنك صاحب هذا الحساب وتوافق على شروط الأمان؟",
                        "Security Agreement & Account Restore!\n\nNotice: you are about to log into an account using its ID.\n" +
                        "1. Keeping your ID secret is entirely your personal responsibility.\n" +
                        "2. Never share your ID with anyone to avoid your in-game belongings being stolen.\n" +
                        "3. The staff will NEVER ask you for your ID inside the game.\n\n" +
                        "Are you sure you own this account and agree to the security terms?"),
                16, U.GOLD, false);
        CheckBox chk = new CheckBox(ctx);
        chk.setText(GameApp.T("أوافق على اتفاقية الأمان وأتحمل المسؤولية", "I agree to the security agreement and take responsibility"));
        chk.setTextColor(Color.WHITE);
        warnLay.addView(warnTxt);
        warnLay.addView(chk);

        AlertDialog.Builder w = new AlertDialog.Builder(ctx);
        w.setTitle(GameApp.T("اتفاقية الأمان", "Security Agreement"));
        w.setView(warnLay);
        w.setCancelable(false);
        w.setPositiveButton(GameApp.T("تأكيد الدخول", "Confirm Login"), (d, i) -> restoreAccount(pid, true));
        w.setNegativeButton(GameApp.T("إلغاء", "Cancel"), null);
        AlertDialog dialog = w.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        chk.setOnCheckedChangeListener((v, checked) -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));
    }

    private void restoreAccount(String pid, boolean showDialog) {
        final ProgressDialog progress = showDialog ? U.progress(ctx, GameApp.T("استعادة", "Restore"), GameApp.T("جاري الاتصال بالسيرفر...", "Connecting to server..."), true) : null;
        new Thread(() -> {
            // 1) Is there a LOCAL copy of this account on this device?
            boolean haveLocal = false;
            if (SaveSystem.hasLocalData() && GameApp.player != null && GameApp.player.username != null
                    && GameApp.player.username.equals(pid)) {
                haveLocal = SaveSystem.load();
            } else if (SaveSystem.hasLocalData() && GameApp.player != null && GameApp.player.username != null
                    && GameApp.player.username.isEmpty() && pid != null && !pid.isEmpty()) {
                // Local file may exist but player not loaded yet; try loading it.
                try {
                    haveLocal = SaveSystem.load();
                    haveLocal = haveLocal && GameApp.player != null && pid.equals(GameApp.player.username);
                } catch (Exception e) {
                    haveLocal = false;
                }
            }

            JSONObject res = null;
            if (!haveLocal) {
                // 2) No local copy -> try the server (fresh device restore).
                boolean up = ServerStatus.isUp(30000);
                res = up ? Db.get("players/" + Db.encode(pid)) : null;
            }

            final JSONObject fRes = res;
            final boolean fHaveLocal = haveLocal;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (progress != null) progress.dismiss();

                // Always: either we have local data, a server copy, or nothing.
                if (fHaveLocal) {
                    // Local is authoritative. Open immediately; push backup in background.
                    finishRestore(pid, true);
                    return;
                }

                if (fRes == null) {
                    // No local and no server copy.
                    GameApp.prefs.edit().putString("saved_id", "").apply();
                    MainActivity.inst.showScreen(new LoginScreen(ctx));
                    U.alert(ctx, GameApp.T("تنبيه", "Notice"),
                            GameApp.T("لم يتم العثور على هذا الحساب في السيرفر ولا توجد نسخة محلية منه.\n"
                                    + "تحقق من الـ ID أو سجّل حساباً جديداً.",
                                    "This account was not found on the server and there is no local copy.\n"
                                            + "Check your ID or register a new account."),
                            GameApp.T("حسنا", "OK"), null);
                    return;
                }

                // Server copy (new device). Load it and treat as the local account.
                GameApp.player = PlayerData.fromJSON(fRes);
                finishRestore(pid, true);
            });
        }).start();
    }

    // Shared completion: applies dev protections, ban/jail checks, saves locally,
    // pushes backup, and opens the game.
    private void finishRestore(String pid, boolean newPlayerLogin) {
        if (GameApp.player == null) {
            MainActivity.inst.showScreen(new LoginScreen(ctx));
            return;
        }
        if (GameApp.isDevUsername(GameApp.player.username)) {
            GameApp.player.isDev = true;
            GameApp.player.isJailed = false;
            GameApp.player.jailUntil = 0;
            GameApp.player.jailReason = null;
            GameApp.player.isBanned = false;
            GameApp.player.bannedUntil = 0;
            GameApp.player.banReason = null;
            GameApp.player.banType = null;
        }
        if (GameApp.player.isDev && GameApp.player.isJailed) {
            GameApp.player.isJailed = false;
            GameApp.player.jailUntil = 0;
            GameApp.player.jailReason = null;
        }
        if (GameApp.player.isBanned) {
            if (GameApp.player.bannedUntil == -1) {
                boolean oldVersion = GameApp.isVersionBanExempt(GameApp.player.username)
                        || (!GameApp.isNeverUnban(GameApp.player.username)
                        && GameApp.isOldVersionBan(GameApp.player.banType, GameApp.player.banReason)
                        && GameApp.LATEST_VERSION.equals(com.lli.com.BuildConfig.VERSION_NAME));
                if (oldVersion) {
                    // Player updated: lift the "old version" ban and keep playing.
                    clearBanLocallyAndOnServer(pid);
                } else {
                    // Permanent ban: re-check the live server record before locking
                    // them out, so an already-lifted player isn't blocked by a stale flag.
                    recheckPermanentBan(pid);
                    return;
                }
            }
            if (GameApp.player.isBanned
                    && GameApp.player.bannedUntil > System.currentTimeMillis() / 1000) {
                String rem = GameApp.player.bannedUntil == -1 ? GameApp.T("إلى الأبد", "Forever") : NumberUtil.formatRemainingTime(GameApp.player.bannedUntil);
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("أنت محظور!", "You Are Banned!"));
                b.setView(U.msg(GameApp.T("تم حظر حسابك.\nالسبب:", "Your account has been banned.\nReason:") + (GameApp.player.banReason == null ? GameApp.T("غير محدد", "Not specified") : GameApp.player.banReason) + GameApp.T("\nالمدة المتبقية:", "\nTime remaining:") + rem));
                b.setCancelable(false);
                b.setNeutralButton(GameApp.T("تواصل معنا (تيليجرام)", "Contact Us (Telegram)"), (d, w) -> openTelegram());
                b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> MainActivity.inst.finish());
                b.show();
                return;
            }
            GameApp.player.isBanned = false;
        }
        if (GameApp.player.isJailed) {
            if (GameApp.player.jailUntil == -1 || GameApp.player.jailUntil > System.currentTimeMillis() / 1000) {
                String rem = GameApp.player.jailUntil == -1 ? GameApp.T("إلى الأبد", "Forever") : NumberUtil.formatRemainingTime(GameApp.player.jailUntil);
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("أنت مسجون!", "You Are Imprisoned!"));
                b.setView(U.msg(GameApp.T("لقد تم سجنك من قبل الإدارة.\nالسبب:", "You have been jailed by the staff.\nReason:") + (GameApp.player.jailReason == null ? GameApp.T("غير محدد", "Not specified") : GameApp.player.jailReason) + GameApp.T("\nالمدة المتبقية:", "\nTime remaining:") + rem));
                b.setCancelable(false);
                b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> MainActivity.inst.finish());
                b.show();
                return;
            } else {
                GameApp.player.isJailed = false;
            }
        }
        if (GameApp.player.lockedChests == null) GameApp.player.lockedChests = new java.util.ArrayList<>();
        GameApp.prefs.edit().putString("saved_id", GameApp.player.username).apply();
        SaveSystem.save();
        SaveSystem.pushCloudAsync();
        SaveSystem.refreshServerBackupNow();
        openMain();
    }

    // For a permanent ban, the server version gate auto-clears it once the
    // client runs the current version. Verify against the live record before
    // locking the player out; if the server no longer bans them, keep playing.
    private void recheckPermanentBan(String pid) {
        final ProgressDialog progress = U.progress(ctx, GameApp.T("التحقق", "Checking"), GameApp.T("جاري التحقق من حالة الحظر...", "Checking ban status..."), true);
        new Thread(() -> {
            boolean stillBanned = true;
            try {
                String path = Db.playerPath().substring(0, Db.playerPath().length() - 1);
                GameApp.player.gameVersion = com.lli.com.BuildConfig.VERSION_NAME;
                Db.put(path, GameApp.player.toJSON());
                JSONObject rec = Db.get(path);
                if (rec != null) stillBanned = rec.optBoolean("is_banned", true);
            } catch (Exception e) {
                stillBanned = GameApp.player.isBanned;
            }
            final boolean fStillBanned = stillBanned;
            final String fReason = GameApp.player.banReason;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (progress != null) progress.dismiss();
                if (!fStillBanned) {
                    GameApp.player.isBanned = false;
                    GameApp.player.bannedUntil = 0;
                    GameApp.player.banReason = null;
                    GameApp.player.banType = null;
                    SaveSystem.save();
                    finishRestore(pid, true);
                    return;
                }
                String rem = GameApp.T("إلى الأبد", "Forever");
                AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                b.setTitle(GameApp.T("أنت محظور!", "You Are Banned!"));
                b.setView(U.msg(GameApp.T("تم حظر حسابك.\nالسبب:", "Your account has been banned.\nReason:") + (fReason == null ? GameApp.T("غير محدد", "Not specified") : fReason) + GameApp.T("\nالمدة المتبقية:", "\nTime remaining:") + rem));
                b.setCancelable(false);
                b.setNeutralButton(GameApp.T("تواصل معنا (تيليجرام)", "Contact Us (Telegram)"), (d, w) -> openTelegram());
                b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> MainActivity.inst.finish());
                b.show();
            });
        }).start();
    }

    // Clears a stale/auto-lifted ban locally and pushes the clean record to the server.
    private void clearBanLocallyAndOnServer(String pid) {
        GameApp.player.isBanned = false;
        GameApp.player.bannedUntil = 0;
        GameApp.player.banReason = null;
        GameApp.player.banType = null;
        SaveSystem.save();
        // Push the clean record on a background thread: doing this on the main
        // thread froze the game for up to ~18s whenever the server was slow.
        new Thread(() -> {
            try {
                String path = Db.playerPath().substring(0, Db.playerPath().length() - 1);
                GameApp.player.gameVersion = com.lli.com.BuildConfig.VERSION_NAME;
                Db.put(path, GameApp.player.toJSON());
            } catch (Exception ignored) {}
        }).start();
    }

    private void openTelegram() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(GameApp.SUPPORT_TELEGRAM_URL));
            ctx.startActivity(i);
        } catch (Exception ignored) {}
    }

    private void openMain() {
        if (GameApp.player != null) GameApp.player.language = GameApp.isEn ? "en" : "ar";
        SaveSystem.saveAndRefresh();
        new Thread(() -> {
            // Staff accounts are immune to device (IP) bans.
            boolean staff = GameApp.player != null && (GameApp.player.isDev || GameApp.player.adminData);
            final JSONObject devBan = staff ? null : com.lli.com.core.Db.deviceBanRecord();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (devBan != null) {
                    final String reason = devBan.optString("reason", GameApp.T("غير محدد", "Not specified"));
                    AlertDialog.Builder b = new AlertDialog.Builder(ctx);
                    b.setTitle(GameApp.T("تم حظر الـ IP الخاص بجهازك!", "Your Device IP Has Been Banned!"));
                    b.setView(U.msg(GameApp.T("تم حظر الـ IP الخاص بجهازك من اللعب.\nالسبب:", "Your device's IP has been banned from playing.\nReason:") + reason + GameApp.T("\n\nسيتم إغلاق اللعبة تلقائيًا...", "\n\nThe game will close automatically...")));
                    b.setCancelable(false);
                    b.setPositiveButton(GameApp.T("إغلاق اللعبة", "Close Game"), (d, w) -> MainActivity.inst.finish());
                    b.show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (MainActivity.inst != null) MainActivity.inst.finish();
                    }, 2000);
                    return;
                }
                LiveNotifier.start();
                GameApp.sound.playSnd("connect.mp3");
                if (MainActivity.inst != null) {
                    MainActivity.inst.showScreen(new DashboardScreen(ctx));
                }
                maybeAskGender();
            });
        }).start();
    }

    // Forces accounts created before the gender field existed to pick one.
    private void maybeAskGender() {
        if (GameApp.player == null) return;
        String g = GameApp.player.gender;
        if ("male".equals(g) || "female".equals(g)) return;
        LinearLayout lay = U.linear(ctx, true);
        lay.setPadding(40, 40, 40, 40);
        lay.addView(U.text(ctx, GameApp.T("يجب اختيار جنس حسابك قبل المتابعة.", "You must choose your account's gender before continuing."), 15, U.WHITE, false));
        lay.addView(U.text(ctx, GameApp.T("اختر جنس بطلك:", "Choose your hero's gender:"), 15, U.GOLD, true));
        final RadioGroup genderGroup = new RadioGroup(ctx);
        genderGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbMale = new RadioButton(ctx);
        rbMale.setText(GameApp.T("ذكر", "Male"));
        rbMale.setTextColor(Color.WHITE);
        rbMale.setId(2001);
        final RadioButton rbFemale = new RadioButton(ctx);
        rbFemale.setText(GameApp.T("أنثى", "Female"));
        rbFemale.setTextColor(Color.WHITE);
        rbFemale.setId(2002);
        genderGroup.addView(rbMale);
        genderGroup.addView(rbFemale);
        lay.addView(genderGroup);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(GameApp.T("اختيار الجنس", "Choose Gender"));
        b.setView(lay);
        b.setCancelable(false);
        b.setPositiveButton(GameApp.T("تأكيد", "Confirm"), (d, w) -> {
            GameApp.player.gender = genderGroup.getCheckedRadioButtonId() == 2002 ? "female" : "male";
            SaveSystem.save();
            SaveSystem.pushCloudAsync();
            U.toast(GameApp.T("تم حفظ جنس الحساب.", "Account gender saved."));
        });
        AlertDialog dialog = b.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        genderGroup.setOnCheckedChangeListener((grp, id) -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(id != -1));
    }
}
