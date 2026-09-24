package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

@Entity(indices = @Index(value = {"url", "type"}, unique = true))
public class Config {

    public static final String BUILTIN_SOURCE_URL = "http://114.55.251.49:5244/d/tvbox/vod.json";
    private static final String BUILTIN_SOURCE_NAME = "内置线路";
    private static final String MANAGED_VOD_KEY = "managed_vod_config";
    private static final String MANAGED_LIVE_KEY = "managed_live_config";


    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    private int id;
    @SerializedName("type")
    private int type;
    @SerializedName("time")
    private long time;
    @SerializedName("url")
    private String url;
    @SerializedName("json")
    private String json;
    @SerializedName("name")
    private String name;
    @SerializedName("logo")
    private String logo;
    @SerializedName("home")
    private String home;
    @SerializedName("parse")
    private String parse;

    @Ignore
    @SerializedName("notice")
    private String notice;
    @Ignore
    @SerializedName("danmaku")
    private String danmaku;

    public static List<Config> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Config.class).getType();
        List<Config> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Config objectFrom(String str) {
        return App.gson().fromJson(str, Config.class);
    }

    public static Config create(int type) {
        return new Config().type(type);
    }

    public static Config create(int type, String url) {
        return new Config().type(type).url(url).insert();
    }

    public static Config create(int type, String url, String name) {
        return new Config().type(type).url(url).name(name).insert();
    }

    public static List<Config> getAll(int type) {
        return AppDatabase.get().getConfigDao().findByType(type);
    }

    public static List<Config> findUrls() {
        return AppDatabase.get().getConfigDao().findUrlByType(0);
    }

    public static void delete(String url) {
        AppDatabase.get().getConfigDao().delete(url);
    }

    public static void delete(String url, int type) {
        AppDatabase.get().getConfigDao().delete(url, type);
    }

    public static Config vod() {
        return managedConfig(0, MANAGED_VOD_KEY);
    }

    public static Config live() {
        return managedConfig(1, MANAGED_LIVE_KEY);
    }

    private static Config managedConfig(int type, String managedKey) {
        Config item = AppDatabase.get().getConfigDao().findOne(type);
        if (item == null) {
            Prefers.put(managedKey, true);
            return create(type, getBuiltinSourceUrl(), BUILTIN_SOURCE_NAME);
        }
        boolean managed = Prefers.getBoolean(managedKey, isManagedUrl(item.getUrl()));
        if (managed && !TextUtils.equals(getBuiltinSourceUrl(), item.getUrl())) {
            item.setUrl(getBuiltinSourceUrl());
            item.setName(BUILTIN_SOURCE_NAME);
            item.update();
        }
        return item;
    }

    public static String getBuiltinSourceUrl() {
        String url = BuildConfig.SOURCE_CONFIG_URL == null ? "" : BuildConfig.SOURCE_CONFIG_URL.trim();
        return TextUtils.isEmpty(url) ? BUILTIN_SOURCE_URL : url;
    }

    public static boolean isManagedUrl(String url) {
        return TextUtils.equals(BUILTIN_SOURCE_URL, url) || TextUtils.equals(getBuiltinSourceUrl(), url);
    }

    public static boolean isManaged(int type, String url) {
        return (type == 0 || type == 1) && isManagedUrl(url);
    }

    public static Config wall() {
        Config item = AppDatabase.get().getConfigDao().findOne(2);
        return item == null ? create(2) : item;
    }

    public static Config find(int id) {
        return AppDatabase.get().getConfigDao().findById(id);
    }

    public static Config find(String url, int type) {
        Config item = AppDatabase.get().getConfigDao().find(url, type);
        return item == null ? create(type, url) : item.type(type);
    }

    public static Config find(String url, String name, int type) {
        Config item = AppDatabase.get().getConfigDao().find(url, type);
        return item == null ? create(type, url, name) : item.type(type).name(name);
    }

    public static Config find(Config config) {
        return find(config, config.getType());
    }

    public static Config find(Config config, int type) {
        Config item = AppDatabase.get().getConfigDao().find(config.getUrl(), type);
        return item == null ? create(type, config.getUrl(), config.getName()) : item.type(type).name(config.getName());
    }

    public static Config find(Depot depot, int type) {
        Config item = AppDatabase.get().getConfigDao().find(depot.getUrl(), type);
        return item == null ? create(type, depot.getUrl(), depot.getName()) : item.type(type).name(depot.getName());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getParse() {
        return parse;
    }

    public void setParse(String parse) {
        this.parse = parse;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }

    public String getDanmaku() {
        return danmaku;
    }

    public void setDanmaku(String danmaku) {
        this.danmaku = danmaku;
    }

    public Config type(int type) {
        setType(type);
        return this;
    }

    public Config url(String url) {
        setUrl(url);
        return this;
    }

    public Config json(String json) {
        setJson(json);
        return this;
    }

    public Config name(String name) {
        setName(name);
        return this;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public String getDesc() {
        if (isManaged(getType(), getUrl())) return TextUtils.isEmpty(getName()) ? BUILTIN_SOURCE_NAME : getName();
        if (!TextUtils.isEmpty(getName())) return getName();
        if (!TextUtils.isEmpty(getUrl())) return getUrl();
        return "";
    }

    public Config insert() {
        if (isEmpty()) return this;
        setId(Math.toIntExact(AppDatabase.get().getConfigDao().insert(this)));
        return this;
    }

    public Config save() {
        if (isEmpty()) return this;
        AppDatabase.get().getConfigDao().insertOrUpdate(this);
        return this;
    }

    public Config update() {
        if (isEmpty()) return this;
        setTime(System.currentTimeMillis());
        if (getType() == 0) Prefers.put(MANAGED_VOD_KEY, isManagedUrl(getUrl()));
        if (getType() == 1) Prefers.put(MANAGED_LIVE_KEY, isManagedUrl(getUrl()));
        Prefers.put("config_" + getType(), getUrl());
        return save();
    }

    public void delete() {
        AppDatabase.get().getConfigDao().delete(getUrl(), getType());
        History.delete(getId());
        Keep.delete(getId());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Config it)) return false;
        return getId() == it.getId();
    }
}
