package com.lli.com.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import com.lli.com.GameApp;
import com.lli.com.core.ItemData;
import com.lli.com.core.NumberUtil;
import com.lli.com.core.PlayerData;
import com.lli.com.core.PlayerSystem;
import com.lli.com.core.SaveSystem;
import com.lli.com.core.ZombieProgress;

import java.util.HashSet;
import java.util.Set;

public class ZombieWorldSystem {
    private static Dialog zDialog;
    private static MediaPlayer fightSound;
    private static String currentWeaponL = "قبضة اليد";
    private static String currentWeaponR = "قبضة اليد";
    private static double originalHp = 100;
    private static double originalMaxHp = 100;

    private static final String[] DIRECTIONS = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
    private static final String[] DIRECTIONS_EN = {"North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest"};
    private static final String[] BOSS_NAMES = {"زاعق الأموات", "نابش الظلام", "ساحق العظام", "راعد القبور"};
    private static final String[] BOSS_NAMES_EN = {"Death Shouter", "Dark Digger", "Bone Crusher", "Grave Thunderer"};
    private static final String[] Z_TYPES = {"راكض القبور", "نابش الأرض", "سارب الليل", "متحول الظلام", "مدرع العفن", "غول المقابر"};
    private static final String[] Z_TYPES_EN = {"Grave Runner", "Earth Digger", "Night Slitherer", "Dark Mutant", "Mold Armor", "Cemetery Ogre"};

    private static String pick(String[] ar, String[] en) {
        int i = NumberUtil.rand(0, ar.length - 1);
        return GameApp.T(ar[i], en[i]);
    }
    private static final String[] DROP_ITEMS = {"قلب الزومبي الملكي", "روح الزومبي المظلمة", "ريشة الزومبي المقدسة", "عظم الزومبي العملاق", "جوهر الزومبي الأثيري", "ذكاء الزومبي الاصطناعي", "معدن الزومبي الصلب", "غبار الزومبي النجمي", "نواة الزومبي النووية", "دم الزومبي الأزلي"};
    private static final String[] DROP_ITEMS_EN = {"Royal Zombie Heart", "Dark Zombie Soul", "Sacred Zombie Feather", "Giant Zombie Bone", "Ethereal Zombie Essence", "Artificial Zombie Intelligence", "Solid Zombie Metal", "Stellar Zombie Dust", "Nuclear Zombie Core", "Eternal Zombie Blood"};
    private static final String[] WEAPONS = {"سيف حديدي", "فأس قتالي", "نصل الظلام", "سيف الليزر المتطور", "محطم الزومبي الأسطوري"};
    private static final String[] WEAPONS_EN = {"Iron Sword", "Battle Axe", "Blade of Darkness", "Advanced Laser Sword", "Legendary Zombie Destroyer"};
    private static final String[] SND_FILES = {"صراع الوحش.mp3", "صراع الوحش اثنين.mp3", "صراع الوحش ثلاثة.mp3", "صراع الوحش اربعة.mp3", "صراع الوحش خمسة.mp3"};

    private static String dropItem(int i) {
        return GameApp.T(DROP_ITEMS[i], DROP_ITEMS_EN[i]);
    }

    private static String fistName() {
        return GameApp.T("قبضة اليد", "Fist");
    }

    private static int weaponIndex(String name) {
        if (name == null) return 0;
        if (name.equals(fistName()) || name.equals("قبضة اليد")) return 0;
        for (int i = 0; i < WEAPONS.length; i++) {
            if (WEAPONS[i].equals(name) || WEAPONS_EN[i].equals(name)) return i + 1;
        }
        return 0;
    }

    private static String weaponName(int idx) {
        if (idx <= 0) return fistName();
        if (idx > WEAPONS.length) idx = WEAPONS.length;
        return GameApp.T(WEAPONS[idx - 1], WEAPONS_EN[idx - 1]);
    }

    private static void speak(String s) {
        GameApp.speakTts(s);
    }

    private static void stopFightSound() {
        if (fightSound != null) {
            try { fightSound.stop(); fightSound.release(); } catch (Exception ignored) {}
            fightSound = null;
        }
    }

    private static void playFightSound() {
        stopFightSound();
        fightSound = GameApp.sound.playSnd(SND_FILES[NumberUtil.rand(0, SND_FILES.length - 1)], true);
    }

    public static void enterZombieWorld() {
        final Context ctx = GameApp.uiCtx();
        final PlayerData p = GameApp.player;

        ZombieProgress prog = p.zombieProgress;
        if (prog == null) {
            prog = new ZombieProgress();
            prog.x = 0;
            prog.y = 0;
            prog.kills = 0;
            prog.hp = 300;
            p.zombieProgress = prog;
        }

        final int[] pos = {prog.x, prog.y};
        final int[] kills = {prog.kills};
        final double[] zHp = {prog.hp};
        final int[] direction = {0};
        final int[] stepsCount = {0};
        final double[] dmgBoost = {1.0};
        final MonsterRef[] monster = {null};
        final boolean[] hasObstacle = {false};
        final int[] obstacleWarnings = {0};
        final EventRef[] event = {null};
        final Set<Integer> milestones = new HashSet<>();

        originalHp = p.stats.hp;
        originalMaxHp = p.stats.maxHp;
        p.stats.maxHp = 300;
        p.stats.hp = Math.max(1, Math.min(300, zHp[0]));

        speak(GameApp.T("تم دخول عالم الزومبي. يرجى إيقاف التوك باك أو الجيوش لتتمكن من اللعب باستخدام نظام التحكم المدمج. اسحب للأعلى للمشي للأمام، وللأسفل للخلف، ولليمين واليسار للدوران. اضغط في المنتصف لمعرفة موقعك.", "You have entered the zombie world. Please turn off TalkBack or the troops so you can play using the built-in control system. Swipe up to walk forward, down to walk backward, and right or left to rotate. Tap the center to check your location."));
        com.lli.com.core.Db.serverLog(GameApp.T("دخل عالم الزومبي", "Entered the zombie world"));

        FrameLayout layout = new FrameLayout(ctx);
        layout.setBackgroundColor(0xFF111111);

        final View gameView = new View(ctx) {
            private float startX, startY;
            private float startX2, startY2;

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                int action = e.getAction() & MotionEvent.ACTION_MASK;
                int pointerCount = e.getPointerCount();
                int w = getWidth();
                int h = getHeight();

                if (action == MotionEvent.ACTION_DOWN) {
                    startX = e.getX();
                    startY = e.getY();
                    return true;
                } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    if (pointerCount == 2) {
                        startX = e.getX(0);
                        startY = e.getY(0);
                        startX2 = e.getX(1);
                        startY2 = e.getY(1);
                    }
                    return true;
                } else if (action == MotionEvent.ACTION_POINTER_UP) {
                    if (pointerCount == 2) {
                        float dx2 = e.getX(1) - startX2;
                        float dy2 = e.getY(1) - startY2;
                        if (dx2 > 100 && dy2 < -100) {
                            showWeaponSelectionUI();
                            return true;
                        }
                    }
                    return true;
                } else if (action == MotionEvent.ACTION_UP) {
                    float x = e.getX();
                    float y = e.getY();
                    float dx = x - startX;
                    float dy = y - startY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    if (y < h * 0.3f) {
                        if (dist > 50) {
                            if (dx > 50) {
                                direction[0] = (direction[0] + 1) % 8;
                                speak(GameApp.T("تغيير الاتجاه إلى:", "Changed direction to:") + GameApp.T(DIRECTIONS[direction[0]], DIRECTIONS_EN[direction[0]]));
                            } else if (dx < -50) {
                                direction[0] = (direction[0] - 1 + 8) % 8;
                                speak(GameApp.T("تغيير الاتجاه إلى:", "Changed direction to:") + GameApp.T(DIRECTIONS[direction[0]], DIRECTIONS_EN[direction[0]]));
                            }
                        } else {
                            direction[0] = (direction[0] + 1) % 8;
                            speak(GameApp.T("تغيير الاتجاه إلى:", "Changed direction to:") + GameApp.T(DIRECTIONS[direction[0]], DIRECTIONS_EN[direction[0]]));
                        }
                        return true;
                    }

                    if (dist > 50) {
                        if (monster[0] != null) {
                            speak(GameApp.T("لا يمكنك التحرك! هناك وحش أمامك، يجب عليك هزيمته أولاً.", "You can't move! There is a monster in front of you; you must defeat it first."));
                            return true;
                        }
                        if (hasObstacle[0]) {
                            obstacleWarnings[0] += 1;
                            if (obstacleWarnings[0] >= 3) {
                                obstacleWarnings[0] = 0;
                                hasObstacle[0] = false;
                                pos[1] = Math.max(0, pos[1] - 5);
                                speak(GameApp.T("لقد تجاهلت العقبة كثيراً! تعثرت وعدت 5 خطوات للخلف. الموقع الحالي:", "You ignored the obstacle too much! You tripped and went back 5 steps. Current location:") + pos[0] + GameApp.T("و", "and") + pos[1]);
                            } else {
                                speak(GameApp.T("تحذير! هناك عقبة في طريقك، اضغط أسفل الشاشة للقفز. (تحذير رقم", "Warning! There is an obstacle in your path, tap the bottom of the screen to jump. (Warning number") + obstacleWarnings[0] + ")");
                            }
                            return true;
                        }
                        if (event[0] != null) {
                            speak(GameApp.T("هناك حدث أمامك!", "There is an event in front of you!") + event[0].description + GameApp.T(". يجب عليك التفاعل أولاً.", ". You must interact with it first."));
                            return true;
                        }

                        if (dy < -50) {
                            movePlayer(pos, direction[0]);
                            GameApp.sound.playSnd("steps_zombie.mp3");
                            stepsCount[0] += 1;
                            speak(GameApp.T("مشي للأمام باتجاه", "Walked forward toward") + GameApp.T(DIRECTIONS[direction[0]], DIRECTIONS_EN[direction[0]]) + GameApp.T(". الموقع:", ". Location:") + pos[0] + GameApp.T("و", "and") + pos[1]);
                            if (stepsCount[0] >= 50) {
                                stepsCount[0] = 0;
                                triggerBossZombie(ctx, monster, dmgBoost, kills);
                            } else {
                                checkZombieEvents(ctx, pos, kills, milestones, monster, hasObstacle, obstacleWarnings, event, dmgBoost, zHp);
                            }
                        } else if (dy > 50) {
                            movePlayer(pos, (direction[0] + 4) % 8);
                            GameApp.sound.playSnd("steps_zombie.mp3");
                            speak(GameApp.T("مشي للخلف. الموقع:", "Walked backward. Location:") + pos[0] + GameApp.T("و", "and") + pos[1]);
                            checkZombieEvents(ctx, pos, kills, milestones, monster, hasObstacle, obstacleWarnings, event, dmgBoost, zHp);
                        }
                    } else {
                        if (x > w * 0.3f && x < w * 0.7f && y > h * 0.3f && y < h * 0.7f) {
                            speak(GameApp.T("إحداثياتك الحالية هي:", "Your current coordinates are:") + pos[0] + GameApp.T("عرض، و", "width, and") + pos[1] + GameApp.T("طول. الهدف عند 500 و 500.", "length. The goal is at 500 and 500."));
                        } else if (x > w * 0.3f && x < w * 0.7f && y > h * 0.8f) {
                            if (hasObstacle[0]) {
                                hasObstacle[0] = false;
                                obstacleWarnings[0] = 0;
                                speak(GameApp.T("قفز! تخطيت العقبة بنجاح. يمكنك الآن استئناف المشي.", "Jumped! You successfully cleared the obstacle. You can now resume walking."));
                            } else if (event[0] != null) {
                                handleZombieEvent(ctx, event, pos, kills, zHp, monster);
                            } else {
                                speak(GameApp.T("قفز!", "Jump!"));
                            }
                        } else if (x < w * 0.5f && y > h * 0.5f) {
                            if (event[0] != null) {
                                handleZombieEvent(ctx, event, pos, kills, zHp, monster);
                            } else {
                                attackZombie(ctx, monster, dmgBoost, kills, zHp, pos);
                            }
                        }
                    }
                    return true;
                }
                return true;
            }
        };
        layout.addView(gameView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button exitBtn = U.btn(ctx, GameApp.T("خروج من عالم الزومبي", "Exit the Zombie World"));
        exitBtn.setAlpha(0.2f);
        exitBtn.setOnClickListener(v -> {
            p.zombieProgress = new ZombieProgress();
            p.zombieProgress.x = pos[0];
            p.zombieProgress.y = pos[1];
            p.zombieProgress.kills = kills[0];
            p.zombieProgress.hp = Math.max(1, zHp[0]);
            stopFightSound();
            p.stats.hp = originalHp;
            p.stats.maxHp = originalMaxHp;
            SaveSystem.saveAndRefresh();
            dismissAndReturn();
        });
        FrameLayout.LayoutParams exitLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP | android.view.Gravity.LEFT);
        layout.addView(exitBtn, exitLp);

        final Dialog d = new Dialog(ctx);
        zDialog = d;
        d.setCancelable(false);
        d.setContentView(layout);
        d.show();
        GameApp.sound.setAmbient("amb_zombie.mp3");
    }

    private static void dismissAndReturn() {
        if (zDialog != null) {
            try { zDialog.dismiss(); } catch (Exception ignored) {}
            zDialog = null;
        }
        GameApp.sound.setAmbient("amb_wolves.mp3");
        ExplorationSystem.returnToMain();
    }

    private static void movePlayer(int[] pos, int dir) {
        if (dir == 0) {
            pos[1] = Math.min(500, pos[1] + 1);
        } else if (dir == 1) {
            pos[1] = Math.min(500, pos[1] + 1);
            pos[0] = Math.min(500, pos[0] + 1);
        } else if (dir == 2) {
            pos[0] = Math.min(500, pos[0] + 1);
        } else if (dir == 3) {
            pos[1] = Math.max(0, pos[1] - 1);
            pos[0] = Math.min(500, pos[0] + 1);
        } else if (dir == 4) {
            pos[1] = Math.max(0, pos[1] - 1);
        } else if (dir == 5) {
            pos[1] = Math.max(0, pos[1] - 1);
            pos[0] = Math.max(0, pos[0] - 1);
        } else if (dir == 6) {
            pos[0] = Math.max(0, pos[0] - 1);
        } else if (dir == 7) {
            pos[1] = Math.min(500, pos[1] + 1);
            pos[0] = Math.max(0, pos[0] - 1);
        }
    }

    private static void triggerBossZombie(Context ctx, MonsterRef[] monster, double[] dmgBoost, int[] kills) {
        String bossName = pick(BOSS_NAMES, BOSS_NAMES_EN);
        double bossHp = 1000 + (kills[0] * 100);
        monster[0] = new MonsterRef(bossName, bossHp, true, false);
        speak(GameApp.T("تحذير! ظهر زعيم الزومبي", "Warning! The zombie boss appeared:") + bossName + GameApp.T("! قوته هائلة، استعد للقتال!", "! His power is immense, get ready to fight!"));
        playFightSound();
    }

    private static void checkZombieEvents(Context ctx, int[] pos, int[] kills, Set<Integer> milestones, MonsterRef[] monster, boolean[] hasObstacle, int[] obstacleWarnings, EventRef[] event, double[] dmgBoost, double[] zHp) {
        PlayerData p = GameApp.player;
        int px = pos[0];
        int py = pos[1];
        int[] milestoneList = {50, 100, 150, 200};
        for (int m : milestoneList) {
            if ((px >= m || py >= m) && !milestones.contains(m)) {
                milestones.add(m);
                ItemData reward = com.lli.com.core.ItemGenerator.generateRandomItem(p.level + (int) Math.floor(m / 10.0), 0);
                p.inventory.add(reward);
                speak(GameApp.T("مبروك! وصلت للنقطة", "Congratulations! You reached point") + m + GameApp.T("وحصلت على مكافأة:", "and received a reward:") + reward.name + GameApp.T(". تم إضافتها لشنطتك.", ". It has been added to your bag."));
            }
        }
        if (px >= 500 && py >= 500) {
            winZombieWorld();
            return;
        }
        if (NumberUtil.rand100() <= 25) {
            String mType = pick(Z_TYPES, Z_TYPES_EN);
            double monsterHp = 80 + (px / 3.0) + (kills[0] * 20);
            monster[0] = new MonsterRef(mType, monsterHp, false, false);
            speak(GameApp.T("تحذير!", "Warning!") + mType + GameApp.T("أمامك مباشرة. تم تعطيل الحركة، اضرب الوحش الآن!", "is directly in front of you. Movement is disabled, hit the monster now!"));
            playFightSound();
        } else if (NumberUtil.rand100() <= 15) {
            hasObstacle[0] = true;
            obstacleWarnings[0] = 0;
            speak(GameApp.T("تحذير! هناك حفرة عميقة أمامك. تم تعطيل الحركة، اضغط أسفل الشاشة للقفز لتخطيها.", "Warning! There is a deep pit in front of you. Movement is disabled, tap the bottom of the screen to jump over it."));
        } else if (NumberUtil.rand100() <= 20) {
            triggerRandomZombieEvent(ctx, event);
        }
    }

    private static void triggerRandomZombieEvent(Context ctx, EventRef[] event) {
        String[][] events = {
                {"merchant", GameApp.T("قابلت تاجراً غامضاً يعرض عليك صندوقاً سحرياً", "You met a mysterious merchant offering you a magic box"), GameApp.T("فتح الصندوق (اضغط يسار الشاشة)", "Open the box (tap left of screen)"), GameApp.T("تكملة المشي (اضغط أسفل الشاشة)", "Continue walking (tap bottom of screen)")},
                {"mythical_beast", GameApp.T("ظهر أمامك وحش خرافي عملاق يلمع باللون الذهبي!", "A giant mythical beast appeared before you, glowing gold!"), GameApp.T("قتال الوحش (اضغط يسار الشاشة)", "Fight the beast (tap left of screen)"), GameApp.T("الهروب وتكملة المشي (اضغط أسفل الشاشة)", "Flee and continue walking (tap bottom of screen)")},
                {"mysterious_well", GameApp.T("وجدت بئراً غامضة تشع منها طاقة غريبة", "You found a mysterious well radiating strange energy"), GameApp.T("الشرب من البئر (اضغط يسار الشاشة)", "Drink from the well (tap left of screen)"), GameApp.T("تجاهل البئر (اضغط أسفل الشاشة)", "Ignore the well (tap bottom of screen)")},
                {"diamond_cache", GameApp.T("عثرت على مخبأ سري للألماس وسط الأنقاض!", "You came across a secret diamond stash amid the ruins!"), GameApp.T("جمع الألماس (اضغط يسار الشاشة)", "Collect the diamonds (tap left of screen)"), GameApp.T("تجاهل المخبأ (اضغط أسفل الشاشة)", "Ignore the stash (tap bottom of screen)")},
                {"crystal_shard", GameApp.T("وجدت بلورة كريستال نادرة تلمع في الظلام", "You found a rare crystal shard glowing in the dark"), GameApp.T("التقاط الكريستال (اضغط يسار الشاشة)", "Pick up the crystal (tap left of screen)"), GameApp.T("ترك الكريستال (اضغط أسفل الشاشة)", "Leave the crystal (tap bottom of screen)")},
                {"equipment_drop", GameApp.T("وجدت جثة محارب قديم وبجانبه معدات تلمع", "You found an ancient warrior's corpse with gleaming equipment beside it"), GameApp.T("تفتيش الجثة (اضغط يسار الشاشة)", "Search the corpse (tap left of screen)"), GameApp.T("تكملة المشي (اضغط أسفل الشاشة)", "Continue walking (tap bottom of screen)")},
                {"hidden_trap", GameApp.T("تعثرت في فخ مخفي وسقطت في حفرة!", "You stumbled into a hidden trap and fell into a pit!"), GameApp.T("محاولة الخروج (اضغط يسار الشاشة)", "Try to get out (tap left of screen)"), GameApp.T("الزحف للخلف (اضغط أسفل الشاشة)", "Crawl backward (tap bottom of screen)")},
        };
        String[] ev = events[NumberUtil.rand(0, events.length - 1)];
        event[0] = new EventRef(ev[0], ev[1], ev[2], ev[3]);
        GameApp.sound.playSnd("تنبيه الحدث.mp3");
        speak(GameApp.T("حدث مفاجئ!", "Surprise event!") + ev[1] + GameApp.T(". الخيارات:", ". Options:") + ev[2] + GameApp.T("أو", "or") + ev[3]);
    }

    private static void handleZombieEvent(Context ctx, EventRef[] event, int[] pos, int[] kills, double[] zHp, MonsterRef[] monster) {
        if (event[0] == null) return;
        EventRef ev = event[0];
        event[0] = null;
        PlayerData p = GameApp.player;
        if (ev.type.equals("merchant")) {
            int outcome = NumberUtil.rand(1, 3);
            if (outcome == 1) {
                long gold = NumberUtil.rand(1000, 3000);
                p.gold += gold;
                speak(GameApp.T("مبروك! فتحت الصندوق ووجدت", "Congratulations! You opened the box and found") + gold + GameApp.T("قطعة ذهبية نادرة!", "rare gold pieces!"));
            } else if (outcome == 2) {
                double damage = NumberUtil.rand(20, 50);
                zHp[0] = Math.max(1, zHp[0] - damage);
                p.stats.hp = zHp[0];
                speak(GameApp.T("الصندوق كان فخاً! تعرضت للطعن بمقدار", "The box was a trap! You were stabbed for") + (long) damage + GameApp.T("ضرر!", "damage!"));
            } else {
                speak(GameApp.T("الصندوق فارغ! التاجر اختفى بضحكة.", "The box was empty! The merchant disappeared with a laugh."));
            }
        } else if (ev.type.equals("mythical_beast")) {
            monster[0] = new MonsterRef(GameApp.T("الوحش الخرافي", "The Mythical Beast"), 500 + (kills[0] * 50), false, true);
            playFightSound();
            speak(GameApp.T("بدأت معركة ملحمية ضد الوحش الخرافي! استعد للضرب.", "An epic battle against the mythical beast has begun! Get ready to strike."));
        } else if (ev.type.equals("mysterious_well")) {
            int outcome = NumberUtil.rand(1, 2);
            if (outcome == 1) {
                p.stats.maxHp += 50;
                zHp[0] = Math.max(zHp[0], p.stats.maxHp);
                p.stats.hp = zHp[0];
                speak(GameApp.T("شربت من الماء السحري! زادت صحتك القصوى بـ 50 وتم شفاؤك بالكامل.", "You drank from the magic water! Your max health increased by 50 and you were fully healed."));
            } else {
                double damage = NumberUtil.rand(40, 80);
                zHp[0] = Math.max(1, zHp[0] - damage);
                p.stats.hp = zHp[0];
                speak(GameApp.T("الماء مسموم! تعرضت لتسمم بمقدار", "The water is poisoned! You were poisoned for") + (long) damage + GameApp.T("ضرر!", "damage!"));
            }
        } else if (ev.type.equals("diamond_cache")) {
            int diamonds = NumberUtil.rand(1, 3);
            p.diamonds += diamonds;
            speak(GameApp.T("جمعت", "You collected") + diamonds + GameApp.T("ألماس من المخبأ! يا لك من محظوظ.", "diamonds from the stash! How lucky you are."));
        } else if (ev.type.equals("crystal_shard")) {
            int crystals = NumberUtil.rand(2, 5);
            p.crystals += crystals;
            speak(GameApp.T("التقطت الكريستال وحصلت على", "You picked up the crystal and got") + crystals + GameApp.T("قطع كريستال!", "crystal pieces!"));
        } else if (ev.type.equals("equipment_drop")) {
            ItemData item = com.lli.com.core.ItemGenerator.generateRandomItem(p.level, 0);
            p.inventory.add(item);
            speak(GameApp.T("وجدت", "You found") + item.name + GameApp.T("! تم إضافة المعدات إلى حقيبتك.", "! The equipment has been added to your bag."));
        } else if (ev.type.equals("hidden_trap")) {
            int backSteps = NumberUtil.rand(3, 7);
            pos[0] = Math.max(0, pos[0] - backSteps);
            pos[1] = Math.max(0, pos[1] - backSteps);
            speak(GameApp.T("لقد تسبب الفخ في تراجعك", "The trap caused you to move back") + backSteps + GameApp.T("خطوات للخلف! الموقع الحالي:", "steps backward! Current location:") + pos[0] + GameApp.T("و", "and") + pos[1]);
        }
        SaveSystem.saveAndRefresh();
    }

    private static void attackZombie(Context ctx, MonsterRef[] monster, double[] dmgBoost, int[] kills, double[] zHp, int[] pos) {
        if (monster[0] == null) {
            speak(GameApp.T("لا يوجد وحوش هنا حالياً.", "There are no monsters here right now."));
            return;
        }
        MonsterRef m = monster[0];
        PlayerData p = GameApp.player;
        double dmg = 10;
        String weaponName = currentWeaponL;
        int wIdx = weaponIndex(weaponName);
        if (wIdx >= 4) dmg = 60;
        else if (wIdx >= 3) dmg = 25;
        dmg += 0; // race skill bonus removed

        m.hp -= dmg;
        GameApp.sound.playSnd(new String[]{"hit_enemy1.mp3", "hit_enemy2.mp3", "hit_enemy3.mp3", "hit_enemy4.mp3"}[NumberUtil.rand(0, 3)]);
        speak(GameApp.T("ضربت الـ", "You hit the") + m.name + GameApp.T("بـ", "with") + weaponName + GameApp.T(". الضرر:", ". Damage:") + (long) dmg);

        if (m.hp <= 0) {
            String mName = m.name;
            boolean wasMythical = m.isMythical;
            monster[0] = null;
            stopFightSound();
            if (NumberUtil.rand(1, 2) == 1) GameApp.sound.playSnd("موت الوحش.mp3");
            else GameApp.sound.playSnd("موت الوحش اثنين.mp3");
            GameApp.sound.playSnd("zombie_kill.mp3");
            GameApp.sound.playSnd("kill_enemy.mp3");

            kills[0] += 1;
            dmgBoost[0] = dmgBoost[0] * 1.20;

if (NumberUtil.rand100() <= 12) {
                String dropped = dropItem(NumberUtil.rand(0, DROP_ITEMS.length - 1));
                int count = NumberUtil.rand(1, 3);
                boolean found = false;
                for (ItemData inv : p.inventory) {
                    if (inv != null && inv.name.equals(dropped)) {
                        inv.count += count;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    ItemData it = new ItemData();
                    it.name = dropped;
                    it.count = count;
                    it.desc = GameApp.T("مادة نادرة تستخدم لفتح الأعراق الأسطورية.", "A rare material used to unlock the legendary races.");
                    p.inventory.add(it);
                }
                speak(GameApp.T("لقد سقط من الوحش:", "The monster dropped:") + dropped + GameApp.T("(عدد", "(count") + count + ")");
            }

            GameApp.sound.playSnd("reward.mp3");
            double hpBonus = 100;
            p.stats.maxHp = (p.stats.maxHp) + hpBonus;
            zHp[0] = Math.max(zHp[0], p.stats.maxHp);
            p.stats.hp = zHp[0];

            String rewardText = GameApp.T("لقد قتلت الـ", "You killed the") + mName + "!";
            if (wasMythical) {
                long bonusGold = 5000;
                p.gold += bonusGold;
                rewardText = GameApp.T("مذهل! لقد هزمت وحشاً خرافياً! حصلت على هدية رائعة:", "Amazing! You defeated a mythical beast! You received a wonderful gift:") + bonusGold + GameApp.T("ذهبة إضافية.", "extra gold.");
            }
            speak(rewardText + GameApp.T("حصلت على سلاح أكثر تطوراً وجوائز قيمة. تم استعادة صحتك وزيادتها بـ", "You got a more advanced weapon and valuable prizes. Your health was restored and increased by") + (long) hpBonus + GameApp.T(". يمكنك الآن استئناف المشي.", ". You can now resume walking."));
            upgradeWeapon();

            double difficulty = dmgBoost[0];
            long goldReward = (long) Math.floor(800 * difficulty);
            long expReward = (long) Math.floor(400 * difficulty);
            p.gold += goldReward;
            PlayerSystem.addExp(expReward);

            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(GameApp.T("انتصار!", "Victory!"));
            b.setView(U.msg(GameApp.T("لقد هزمت", "You defeated") + mName + GameApp.T("وحصلت على مكافآت:\n +", "and received rewards:\n +") + goldReward + GameApp.T("ذهب\n +", "gold\n +") + expReward + GameApp.T("خبرة\nتم استعادة صحتك بالكامل وزيادة صحتك القصوى بـ", "exp\nYour health was fully restored and your max health increased by") + (long) hpBonus + GameApp.T("!\nتم فتح الحركة مجدداً.", "!\nMovement has been re-enabled.")));
            b.setPositiveButton(GameApp.T("استمرار", "Continue"), null);
            b.show();
        } else {
            double mDmg = 5 * dmgBoost[0];
            if (m.isBoss) mDmg = mDmg * 3;
            double fightElapsed = (System.currentTimeMillis() - m.spawnTime) / 1000.0;
            double totalDef = com.lli.com.core.BattleDecay.effective(p.stats.endurance + p.tempStats.endurance, fightElapsed);
            double totalAgi = com.lli.com.core.BattleDecay.effective(p.stats.agility + p.tempStats.agi, fightElapsed);
            double armorPierce = mDmg * 0.15;
            mDmg = mDmg + armorPierce;
            double defRed = Math.min(mDmg * 0.6, totalDef / 15.0);
            mDmg = Math.max(mDmg * 0.3, mDmg - defRed);
            double dodgeChance = Math.min(40, totalAgi / 10.0);
            if (totalAgi > 1000) {
                dodgeChance = Math.max(5, dodgeChance - (totalAgi - 1000) / 5000.0);
            }
            if (NumberUtil.rand100() <= dodgeChance) {
                mDmg = 0;
                speak(GameApp.T("لقد تفاديت ضربة زومبي!", "You dodged a zombie's attack!"));
            }
            if (mDmg > 0) {
                mDmg = Math.max(mDmg, (p.stats.maxHp) * 0.02);
            }
            zHp[0] = zHp[0] - mDmg;
            p.stats.hp = zHp[0];
            GameApp.sound.playSnd(new String[]{"zombie_voice_1.mp3", "zombie_voice_2.mp3", "zombie_voice_3.mp3", "zombie_voice_4.mp3", "zombie_voice_5.mp3"}[NumberUtil.rand(0, 4)]);
            speak(GameApp.T("الوحش يهاجمك! صحتك الحالية:", "The monster is attacking you! Your current health:") + (long) Math.floor(zHp[0]));
            if (zHp[0] <= 0) {
                speak(GameApp.T("لقد مت في عالم الزومبي. ستعود لنقطة البداية وتم إعادة ضبط الصعوبة.", "You died in the zombie world. You will return to the start point and the difficulty has been reset."));
                p.stats.hp = originalHp;
                p.stats.maxHp = originalMaxHp;
                pos[0] = 0;
                pos[1] = 0;
                kills[0] = 0;
                dmgBoost[0] = 1.0;
                monster[0] = null;
                stopFightSound();
                zHp[0] = originalHp;
            }
        }
        SaveSystem.saveAndRefresh();
    }

    private static void upgradeWeapon() {
        int cur = weaponIndex(currentWeaponL);
        int next = Math.min(WEAPONS.length, cur + 1);
        String nextW = weaponName(next);
        currentWeaponL = nextW;
        currentWeaponR = nextW;
        speak(GameApp.T("تم تطوير سلاحك إلى:", "Your weapon has been upgraded to:") + nextW);
    }

    private static void showWeaponSelectionUI() {
        String[] options = {GameApp.T("اليد اليمنى فقط", "Right hand only"), GameApp.T("اليد اليسرى فقط", "Left hand only"), GameApp.T("استخدام السلاحين معاً", "Use both weapons together")};
        AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
        b.setTitle(GameApp.T("اختر وضع القتال", "Choose Combat Mode"));
        b.setItems(options, (d, i) -> {
            if (i == 0) {
                currentWeaponL = fistName();
                currentWeaponR = fistName();
                speak(GameApp.T("اخترت اليد اليمنى.", "You chose the right hand."));
            } else if (i == 1) {
                currentWeaponL = fistName();
                currentWeaponR = fistName();
                speak(GameApp.T("اخترت اليد اليسرى.", "You chose the left hand."));
            } else {
                speak(GameApp.T("أنت الآن تمشي بسلاحين، يمكنك الضرب بكلتا اليدين.", "You now walk with two weapons; you can strike with both hands."));
            }
        });
        b.show();
    }

    private static void winZombieWorld() {
        PlayerData p = GameApp.player;
        stopFightSound();
        p.stats.hp = originalHp;
        p.stats.maxHp = originalMaxHp;
        String prize = GameApp.T("جوهر الأساطير النادر", "The Rare Legend Essence");
        p.essence += 100;
        p.gold += 50000;
        speak(GameApp.T("مبروك! لقد وصلت إلى نهاية العالم وفزت بالجائزة الكبرى:", "Congratulations! You reached the end of the world and won the grand prize:") + prize + GameApp.T("بالإضافة إلى 50 ألف ذهبة. أنت بطل حقيقي!", "plus 50 thousand gold. You are a true hero!"));
        SaveSystem.saveAndRefresh();
        AlertDialog.Builder b = new AlertDialog.Builder(GameApp.uiCtx());
        b.setTitle(GameApp.T("انتصار ساحق!", "Overwhelming Victory!"));
        b.setView(U.msg(GameApp.T("لقد غزوت عالم الزومبي وحصلت على أندر موارد اللعبة.", "You conquered the zombie world and obtained the rarest resources in the game.")));
        b.setPositiveButton(GameApp.T("العودة للمدينة", "Return to City"), (d, w) -> dismissAndReturn());
        b.setCancelable(false);
        b.show();
    }

    private static class MonsterRef {
        String name;
        double hp;
        boolean isBoss;
        boolean isMythical;
        long spawnTime;

        MonsterRef(String n, double h, boolean boss, boolean mythical) {
            name = n;
            hp = h;
            isBoss = boss;
            isMythical = mythical;
            spawnTime = System.currentTimeMillis();
        }
    }

    private static class EventRef {
        String type;
        String description;
        String option1Text;
        String option2Text;

        EventRef(String t, String d, String o1, String o2) {
            type = t;
            description = d;
            option1Text = o1;
            option2Text = o2;
        }
    }
}
