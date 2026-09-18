package com.lli.com.core;

import android.media.MediaPlayer;

import com.lli.com.GameApp;
import com.lli.com.R;

import java.util.HashMap;
import java.util.Map;

public class Sound {
    private Map<String, Integer> map = new HashMap<>();
    private MediaPlayer ambientPlayer;
    private String ambientName;

    public Sound() {
        // Environment / zone ambiences (loop when entered the matching place)
        map.put("amb_city.mp3", R.raw.amb_city);
        map.put("amb_bank.mp3", R.raw.amb_bank);
        map.put("amb_market.mp3", R.raw.amb_market);
        map.put("amb_character.mp3", R.raw.amb_character);
        map.put("amb_community.mp3", R.raw.amb_community);
        map.put("amb_demons.mp3", R.raw.amb_demons);
        map.put("amb_goblins.mp3", R.raw.amb_goblins);
        map.put("amb_zombie.mp3", R.raw.amb_zombie);
        map.put("amb_wolves.mp3", R.raw.amb_wolves);
        map.put("amb17.mp3", R.raw.amb17);
        // Arabic ambience aliases
        map.put("أجواء البنك.mp3", R.raw.amb_bank);
        map.put("أجواء السوق.mp3", R.raw.amb_market);
        map.put("أجواء الشخصية.mp3", R.raw.amb_character);
        map.put("أجواء المجتمع.mp3", R.raw.amb_community);
        map.put("أجواء الشياطين.mp3", R.raw.amb_demons);
        map.put("أجواء العفاريت.mp3", R.raw.amb_goblins);
        map.put("أجواء عالم الزونبي.mp3", R.raw.amb_zombie);
        map.put("أجواء الأسود.mp3", R.raw.amb_wolves);
        map.put("صوت المدينة.mp3", R.raw.amb_city);
        // New SFX
        map.put("notify1.mp3", R.raw.notify1);
        map.put("notify2.mp3", R.raw.notify2);
        map.put("notify3.mp3", R.raw.notify3);
        map.put("reward_get.mp3", R.raw.reward_get);
        map.put("capture.mp3", R.raw.capture);
        map.put("death_defeat.mp3", R.raw.death_defeat);
        map.put("steps_zombie.mp3", R.raw.steps_zombie);
        map.put("steps_walk.mp3", R.raw.steps_walk);
        map.put("potion_drink.mp3", R.raw.potion_drink);
        map.put("zombie_voice_1.mp3", R.raw.zombie_voice_a);
        map.put("zombie_voice_2.mp3", R.raw.zombie_voice_b);
        map.put("zombie_voice_3.mp3", R.raw.zombie_voice_c);
        map.put("zombie_voice_4.mp3", R.raw.zombie_voice_d);
        map.put("zombie_voice_5.mp3", R.raw.zombie_voice_e);
        map.put("hit_enemy1.mp3", R.raw.hit_enemy1);
        map.put("hit_enemy2.mp3", R.raw.hit_enemy2);
        map.put("hit_enemy3.mp3", R.raw.hit_enemy3);
        map.put("hit_enemy4.mp3", R.raw.hit_enemy4);
        map.put("connect.mp3", R.raw.connect);
        map.put("kill_enemy.mp3", R.raw.kill_enemy);
        map.put("chest_loot.mp3", R.raw.chest_loot);
        map.put("pre_duel.mp3", R.raw.pre_duel);
        map.put("zombie_kill.mp3", R.raw.zombie_kill);
        // Arabic SFX aliases
        map.put("صوت إشعار.mp3", R.raw.notify2);
        map.put("الحصول على مكافأة.mp3", R.raw.reward_get);
        map.put("الموت أو الهزيمة.mp3", R.raw.death_defeat);
        map.put("خطوات المشي في عالم الزومبي.mp3", R.raw.steps_zombie);
        map.put("خطوات المشي.mp3", R.raw.steps_walk);
        map.put("شرب جرعة.mp3", R.raw.potion_drink);
        map.put("ضرب عدو.mp3", R.raw.hit_enemy1);
        map.put("عند الاتصال باللعبة.mp3", R.raw.connect);
        map.put("عند قتل عدو.mp3", R.raw.kill_enemy);
        map.put("فتح صندوق.mp3", R.raw.chest_loot);
        map.put("قبل بدء المبارزة.mp3", R.raw.pre_duel);
        map.put("قتل زونبي.mp3", R.raw.zombie_kill);
        map.put("ambient_bgm.mp3", R.raw.ambient_bgm);
        map.put("attack.mp3", R.raw.attack);
        map.put("buy.mp3", R.raw.buy);
        map.put("click.mp3", R.raw.click);
        map.put("levelup.mp3", R.raw.levelup);
        map.put("msg.mp3", R.raw.msg);
        map.put("reward.mp3", R.raw.reward);
        map.put("sell.mp3", R.raw.sell);
        map.put("steps.mp3", R.raw.steps);
        map.put("travel.mp3", R.raw.travel);
        map.put("type.mp3", R.raw.type);
        map.put("monster_death.mp3", R.raw.monster_death);
        map.put("monster_death2.mp3", R.raw.monster_death2);
        map.put("event_alert.mp3", R.raw.event_alert);
        map.put("monster_clash_1.mp3", R.raw.monster_clash_1);
        map.put("monster_clash_2.mp3", R.raw.monster_clash_2);
        map.put("monster_clash_3.mp3", R.raw.monster_clash_3);
        map.put("monster_clash_4.mp3", R.raw.monster_clash_4);
        map.put("monster_clash_5.mp3", R.raw.monster_clash_5);
        map.put("sword_hit.mp3", R.raw.sword_hit);
        map.put("boss_growl.mp3", R.raw.boss_growl);
        map.put("zombie_groan.mp3", R.raw.zombie_groan);
        map.put("fireball.mp3", R.raw.fireball);
        map.put("magic.mp3", R.raw.magic);
        map.put("powerup.mp3", R.raw.powerup);
        map.put("coin.mp3", R.raw.coin);
        map.put("chest_open.mp3", R.raw.chest_open);
        map.put("thunder.mp3", R.raw.thunder);
        map.put("heartbeat.mp3", R.raw.heartbeat);
        map.put("victory.mp3", R.raw.victory);
        map.put("dodge.mp3", R.raw.dodge);
        map.put("bow_shot.mp3", R.raw.bow_shot);
        // Arabic aliases (matching Lua usage)
        map.put("تنبيه الحدث.mp3", R.raw.event_alert);
        map.put("موت الوحش.mp3", R.raw.monster_death);
        map.put("موت الوحش اثنين.mp3", R.raw.monster_death2);
        map.put("صراع الوحش.mp3", R.raw.monster_clash_1);
        map.put("صراع الوحش اثنين.mp3", R.raw.monster_clash_2);
        map.put("صراع الوحش ثلاثة.mp3", R.raw.monster_clash_3);
        map.put("صراع الوحش اربعة.mp3", R.raw.monster_clash_4);
        map.put("صراع الوحش خمسة.mp3", R.raw.monster_clash_5);
        map.put("ضرب السيف.mp3", R.raw.sword_hit);
        map.put("هدير الزعيم.mp3", R.raw.boss_growl);
        map.put("أنين الزومبي.mp3", R.raw.zombie_groan);
        map.put("كرة النار.mp3", R.raw.fireball);
        map.put("سحر.mp3", R.raw.magic);
        map.put("تعزيز القوة.mp3", R.raw.powerup);
        map.put("عملة.mp3", R.raw.coin);
        map.put("فتح الصندوق.mp3", R.raw.chest_open);
        map.put("رعد.mp3", R.raw.thunder);
        map.put("نبض القلب.mp3", R.raw.heartbeat);
        map.put("انتصار.mp3", R.raw.victory);
        map.put("تفادي.mp3", R.raw.dodge);
        map.put("ضربة قوس.mp3", R.raw.bow_shot);
        map.put("fail.mp3", R.raw.monster_clash_2);
        map.put("boss_spawn.mp3", R.raw.boss_growl);
        map.put("monster_growl.mp3", R.raw.monster_death);
        // New feature sounds (achievements, auto-exploration, battle log, UI)
        map.put("ach_unlock.mp3", R.raw.ach_unlock);
        map.put("auto_start.mp3", R.raw.auto_start);
        map.put("auto_done.mp3", R.raw.auto_done);
        map.put("tick.mp3", R.raw.tick);
        map.put("log_open.mp3", R.raw.log_open);
        map.put("confirm.mp3", R.raw.confirm);
        map.put("pm_chime.wav", R.raw.pm_chime);
    }

    public MediaPlayer playSnd(String fileName, boolean loop) {
        if (GameApp.isMuted) return null;
        try {
            Integer id = map.get(fileName);
            if (id == null) return null;
            MediaPlayer mp = MediaPlayer.create(GameApp.ctx, id);
            if (mp == null) return null;
            if (loop) {
                mp.setLooping(true);
                mp.start();
                return mp;
            }
            mp.setOnCompletionListener(m -> {
                try { m.release(); } catch (Exception ignored) {}
            });
            mp.start();
            return mp;
        } catch (Exception e) {
            return null;
        }
    }

    public MediaPlayer playSnd(String fileName) {
        return playSnd(fileName, false);
    }

    public void stopSnd(MediaPlayer mp) {
        if (mp != null) {
            try {
                if (mp.isPlaying()) mp.stop();
                mp.release();
            } catch (Exception ignored) {}
        }
    }

    /** Plays a looping zone ambience, stopping the current one first. */
    public synchronized MediaPlayer playAmbient(String fileName) {
        stopAmbient();
        MediaPlayer mp = playSnd(fileName, true);
        if (mp != null) {
            ambientPlayer = mp;
            ambientName = fileName;
        }
        return ambientPlayer;
    }

    /** Stops and releases the current looping ambience. */
    public synchronized void stopAmbient() {
        if (ambientPlayer != null) {
            try {
                if (ambientPlayer.isPlaying()) ambientPlayer.stop();
                ambientPlayer.release();
            } catch (Exception ignored) {}
            ambientPlayer = null;
            ambientName = null;
        }
    }

    /** Plays a looping ambience if it changed, otherwise keeps it running. */
    public synchronized void setAmbient(String fileName) {
        if (fileName == null) {
            stopAmbient();
            return;
        }
        if (ambientName != null && ambientName.equals(fileName)) return;
        playAmbient(fileName);
    }
}
