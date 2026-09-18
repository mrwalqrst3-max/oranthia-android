package com.lli.com;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lli.com.core.SaveSystem;
import com.lli.com.core.VpnGuard;
import com.lli.com.ui.Screen;
import com.lli.com.ui.LoginScreen;
import com.lli.com.ui.DashboardScreen;
import com.lli.com.ui.SettingsSystem;
import com.lli.com.ui.U;

import java.io.StringWriter;

public class MainActivity extends Activity {
    public static MainActivity inst;
    public Screen currentScreen;
    private AlertDialog vpnDialog = null;
    // Bump this whenever the Terms/Privacy change so every existing user re-reads them.
    private static final int TERMS_VERSION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inst = this;
        GameApp.recordEvent("بدء التطبيق");
        startVpnGuard();
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
            }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1002);
            }
        }
        boolean langPicked = GameApp.prefs != null && GameApp.prefs.contains("is_en");
        boolean termsAccepted = GameApp.prefs != null
                && GameApp.prefs.getBoolean("terms_accepted", false)
                && GameApp.prefs.getInt("terms_version", 0) >= TERMS_VERSION;
        GameApp.ensureLogFolder();
        showScreen(new LoginScreen(this));
        if (!langPicked) {
            new Handler(Looper.getMainLooper()).postDelayed(this::showFirstRunLanguageChooser, 600);
        } else if (!termsAccepted) {
            new Handler(Looper.getMainLooper()).postDelayed(this::showTermsConsent, 700);
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (GameApp.hasRecentReport(30 * 60 * 1000L)) {
                String diag = GameApp.readDiagnostics();
                if (diag != null && !diag.contains("لا توجد تفاصيل") && !diag.contains("No saved details")) {
                    try {
                        if (diag.length() > 3500) diag = diag.substring(0, 3500);
                        AlertDialog.Builder b = new AlertDialog.Builder(this);
                        b.setTitle(GameApp.T("سجل الخطأ السابق — صوّره وأرسله", "Previous Error Log - Screenshot and Send It"));
                        b.setView(U.msg(diag));
                        b.setPositiveButton(GameApp.T("حسنا", "OK"), null);
                        b.show();
                    } catch (Exception ignored) {}
                } else {
                    Toast.makeText(this, GameApp.T("تم تسجيل خطأ — ابحث في: Download/ClashLegends", "An error was logged - look in: Download/ClashLegends"), Toast.LENGTH_LONG).show();
                }
            }
        }, 1800);
    }

    private void showFirstRunLanguageChooser() {
        if (isFinishing() || isDestroyed()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(GameApp.T("اختر اللغة", "Choose Language"));
        b.setView(U.msg(GameApp.T("اختر اللغة المناسبة لك:", "Choose your preferred language:")));
        b.setItems(new String[]{"العربية", "English"}, (d, i) -> {
            GameApp.setLanguage(i == 1);
            recreate();
        });
        b.setNegativeButton(GameApp.T("إلغاء", "Cancel"), (d, i) -> GameApp.setLanguage(GameApp.isEn));
        b.setCancelable(false);
        b.show();
    }

    private void showTermsConsent() {
        if (isFinishing() || isDestroyed()) return;
        final AlertDialog[] consentRef = {null};
        // [0] = Privacy Policy read+accepted, [1] = Terms of Use read+accepted.
        final boolean[] agreed = {false, false};
        final LinearLayout lay = U.linear(this, true);
        lay.setPadding(40, 40, 40, 40);

        TextView title = U.text(this, GameApp.T("قبل أن تستمر...", "Before you continue..."), 19, U.GOLD, true);
        lay.addView(title);

        TextView info = U.text(this,
                GameApp.T("\nيجب الضغط على كل زر وقراءة الاتفاقية بالكامل ثم الضغط على زر الموافقة أسفلها. لن يمكنك المتابعة قبل قراءة واعتماد الاتفاقيتين معاً.",
                        "\nYou must tap each button, read the agreement fully and press the accept button at its bottom. You cannot continue before reading and accepting both agreements."),
                14, U.WHITE, false);
        lay.addView(info);

        final Button[] privacyBtn = {null};
        privacyBtn[0] = U.btn(this, GameApp.T("سياسة الخصوصية 📜", "Privacy Policy 📜"), v -> {
            GameApp.sound.playSnd("click.mp3");
            SettingsSystem.openLegalDoc(true, () -> {
                agreed[0] = true;
                if (privacyBtn[0] != null) privacyBtn[0].setText(GameApp.T("سياسة الخصوصية ✅ (تمت القراءة والموافقة)", "Privacy Policy ✅ (read & accepted)"));
                updateConsentButton(consentRef[0], agreed);
            });
        });
        lay.addView(privacyBtn[0], U.lpMargins(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 12, 0, 12));

        final Button[] termsBtn = {null};
        termsBtn[0] = U.btn(this, GameApp.T("شروط الاستخدام 📜", "Terms of Use 📜"), v -> {
            GameApp.sound.playSnd("click.mp3");
            SettingsSystem.openLegalDoc(false, () -> {
                agreed[1] = true;
                if (termsBtn[0] != null) termsBtn[0].setText(GameApp.T("شروط الاستخدام ✅ (تمت القراءة والموافقة)", "Terms of Use ✅ (read & accepted)"));
                updateConsentButton(consentRef[0], agreed);
            });
        });
        lay.addView(termsBtn[0], U.lpMargins(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 12, 0, 12));

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(GameApp.T("سياسة الخصوصية وشروط الاستخدام", "Privacy Policy & Terms of Use"));
        b.setView(lay);
        b.setCancelable(false);
        b.setPositiveButton(GameApp.T("متابعة", "Continue"), (d, w) -> {
            if (!agreed[0] || !agreed[1]) return;
            if (GameApp.prefs != null) {
                GameApp.prefs.edit().putBoolean("terms_accepted", true).putInt("terms_version", TERMS_VERSION).apply();
                Toast.makeText(this, GameApp.T("شكراً لك! تم تسجيل موافقتك.", "Thank you! Your consent has been recorded."), Toast.LENGTH_SHORT).show();
            }
            if (consentRef[0] != null) consentRef[0].dismiss();
        });
        b.setNegativeButton(GameApp.T("لا أوافق", "I Do Not Agree"), (d, w) -> {
            U.alert(this, GameApp.T("تعذر المتابعة", "Cannot Continue"),
                    GameApp.T("لا يمكنك استخدام التطبيق دون الموافقة على الشروط وسياسة الخصوصية.\n\nسيتم إغلاق التطبيق. أعد فتحه واقرأ الشروط جيداً قبل الموافقة.",
                            "You cannot use the app without agreeing to the terms and privacy policy.\n\nClosing the app. Reopen it and read the terms carefully before agreeing."),
                    GameApp.T("حسناً", "OK"), v -> finish());
        });
        consentRef[0] = b.create();
        consentRef[0].show();
        try {
            consentRef[0].getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        } catch (Exception ignored) {}
    }

    private void updateConsentButton(AlertDialog d, boolean[] agreed) {
        if (d == null) return;
        try {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(agreed[0] && agreed[1]);
        } catch (Exception ignored) {}
    }

    public void showScreen(Screen s) {
        currentScreen = s;
        GameApp.recordEvent("شاشة:" + s.getClass().getSimpleName());
        try {
            setContentView(s.build());
        } catch (Throwable t) {
            try {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new java.io.PrintWriter(sw));
                GameApp.writeLog("crash_log.txt", "=== خطأ في بناء الشاشة" + s.getClass().getSimpleName() + "===\n" + sw.toString());
                Toast.makeText(this, GameApp.T("خطأ في فتح الشاشة (سُجّل في Download/ClashLegends)", "Error opening screen (logged in Download/ClashLegends)"), Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            GameApp.recordEvent("لمسة على" + (currentScreen != null ? currentScreen.getClass().getSimpleName() : "?"));
        }
        try {
            return super.dispatchTouchEvent(ev);
        } catch (Throwable t) {
            try {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new java.io.PrintWriter(sw));
                GameApp.recordEvent("خطأ أثناء معالجة اللمس:" + t.getClass().getSimpleName());
                GameApp.writeLog("crash_log.txt", "=== خطأ لمس ===\n" + sw.toString());
                String msg = t.getClass().getSimpleName() + ":" + t.getMessage();
                if (msg != null && msg.length() > 200) msg = msg.substring(0, 200);
                Toast.makeText(this, GameApp.T("خطأ (تم تسجيله في Download/ClashLegends):", "Error (logged in Download/ClashLegends):") + msg, Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
            return true;
        }
    }

    private long lastBackPressed = 0;

    @Override
    public void onBackPressed() {
        if (currentScreen != null && !(currentScreen instanceof LoginScreen)) {
            if (!(currentScreen instanceof DashboardScreen)) {
                GameApp.sound.playSnd("click.mp3");
                showScreen(new DashboardScreen(this));
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastBackPressed > 2000) {
                lastBackPressed = now;
                Toast.makeText(this, GameApp.T("اضغط رجوع مرة أخرى للخروج من اللعبة", "Press back again to exit the game"), Toast.LENGTH_SHORT).show();
                return;
            }
            moveTaskToBack(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        GameApp.sound.stopAmbient();
        if (GameApp.player != null && GameApp.player.username != null && !GameApp.player.username.isEmpty()) {
            SaveSystem.pushCloudAsync();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (GameApp.tts != null) GameApp.tts.stop();
    }

    private void startVpnGuard() {
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable[] holder = new Runnable[1];
        final Runnable check = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        final boolean present = VpnGuard.isVpnPresent(MainActivity.this);
                        h.post(() -> {
                            try {
                                if (present && (vpnDialog == null || !vpnDialog.isShowing())) {
                                    if (vpnDialog != null) { try { vpnDialog.dismiss(); } catch (Exception ignored) {} }
                                    AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                                    b.setTitle(GameApp.T("برنامج VPN مكتشف 🚫", "VPN Detected 🚫"));
                                    b.setView(U.msg(GameApp.T(
                                            "\nتم اكتشاف وجود برنامج VPN على جهازك.\n\nاللعبة لن تعمل حتى تقوم بإلغاء تثبيت جميع برامج الـ VPN.\n\nبعد إلغاء التثبيت اضغط زر التحقق أدناه.",
                                            "\nA VPN program was detected on your device.\n\nThe game will not work until you uninstall all VPN apps.\n\nAfter uninstalling, press the check button below.")));
                                    b.setPositiveButton(GameApp.T("تحقق مجدداً", "Check Again"), (d, w) -> holder[0].run());
                                    b.setNegativeButton(GameApp.T("إغلاق التطبيق", "Close App"), (d, w) -> finish());
                                    b.setCancelable(false);
                                    vpnDialog = b.create();
                                    vpnDialog.setCanceledOnTouchOutside(false);
                                    vpnDialog.show();
                                } else if (!present && vpnDialog != null && vpnDialog.isShowing()) {
                                    vpnDialog.dismiss();
                                    vpnDialog = null;
                                    Toast.makeText(MainActivity.this, GameApp.T("تم إزالة VPN — يمكنك اللعب الآن.", "VPN removed — you can play now."), Toast.LENGTH_LONG).show();
                                }
                            } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}
                }).start();
                h.postDelayed(this, 8000);
            }
        };
        holder[0] = check;
        h.postDelayed(check, 1500);
    }
}
