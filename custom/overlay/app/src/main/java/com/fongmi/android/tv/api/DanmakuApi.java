package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();
    private static final String TAG_AI = TAG + "_AI";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String API_MATCH = "/api/v2/match";
    private static final String API_SEARCH_ANIME = "/api/v2/search/anime";
    private static final String API_SEARCH_EPISODES = "/api/v2/search/episodes";
    private static final String API_BANGUMI = "/api/v2/bangumi/";
    private static final String API_COMMENT = "/api/v2/comment/";
    private static final String TMDB_SEARCH = "https://api.themoviedb.org/3/search/multi";
    private static final ConcurrentHashMap<String, AutoCache> AUTO_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, BodyCache> BODY_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> PROVIDER_FAILURES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> PROVIDER_BLOCKED = new ConcurrentHashMap<>();
    private static volatile JSONObject aliasRules;
    private static final Pattern EPISODE_MARKED_NUMBER = Pattern.compile("第\\s*0*(\\d{1,4})\\s*[集话話期]");
    private static final Pattern EPISODE_MARKED_CHINESE = Pattern.compile("第\\s*([一二三四五六七八九十两俩〇零]{1,4})\\s*[集话話期]");
    private static final Pattern EPISODE_SEASON_NUMBER = Pattern.compile("(?i)S\\d{1,2}E0*(\\d{1,4})");
    private static final Pattern EPISODE_EXPLICIT_NUMBER = Pattern.compile("(?i)(?:^|[._\\-\\s])(?:E|EP)0*(\\d{1,4})(?=$|[._\\-\\s])");
    private static final Pattern EPISODE_X_NUMBER = Pattern.compile("(?i)(?:^|[._\\-\\s])0*(\\d{1,2})x0*(\\d{1,4})(?=$|[._\\-\\s])");
    private static final Pattern EPISODE_LEADING_NUMBER = Pattern.compile("^\\s*(?:第\\s*)?0*(\\d{1,4})(?=\\s*(?:[集话話期]|[._\\-—:：]))");
    private static final Pattern SEASON_NUMBER = Pattern.compile("(?i)(?:S(?:eason)?\\s*0*(\\d{1,2})|第\\s*([一二三四五六七八九十两俩〇零壹贰叁肆伍陆柒捌玖拾0-9]{1,4})\\s*[季部]|Part\\s*0*(\\d{1,2}))");
    private static final Pattern SEASON_RANGE = Pattern.compile("(?i)(?:S(?:eason)?\\s*0*\\d{1,2}|第\\s*[一二三四五六七八九十两俩〇零0-9]+\\s*季)\\s*[-—~～至到]+\\s*(?:S(?:eason)?\\s*)?0*\\d{1,2}");
    private static final Pattern TITLE_BARE_SEASON = Pattern.compile("(?i)(?<!\\d)([2-9]|1\\d|20)(?=[\\s._\\-]*(?:更|更新|更新至|全|完结|已完结|$))");
    private static final Pattern EPISODE_AFTER_SEASON_NUMBER = Pattern.compile("第\\s*[一二三四五六七八九十两俩〇零0-9]+\\s*[季部]\\s*[_\\-:：\\s]+0*(\\d{1,4})(?!\\d)");
    private static final Pattern EPISODE_TRAILING_NUMBER = Pattern.compile("(?:^|[_\\-\\s:：第])0*(\\d{1,4})\\s*(?:[集话話期])?\\s*$");
    private static final Pattern EPISODE_SEASON_TOKEN = Pattern.compile("第\\s*[一二三四五六七八九十两俩〇零0-9]+\\s*[季部]");
    private static final Pattern EPISODE_NUMBER = Pattern.compile("(\\d{1,4})");
    private static final Pattern EPISODE_EXTRA = Pattern.compile("(?i)(小剧场|预告|先导|花絮|特辑|彩蛋|番外|制作|采访|幕后|加更|超前|直拍|纯享|速看|解说|混剪|未播|删减片段|会员(?:专享|加长|版)|精编|特别版|vlog|trailer|preview|teaser|(?:^|[\\s【\\[])s?p\\d*(?:$|[\\s:：.\\-】\\]]))");
    private static final Pattern TITLE_SOURCE = Pattern.compile("(?i)\\bfrom\\s+([a-z0-9_&\\-]+)\\s*$");
    private static final Pattern EPISODE_SOURCE_TAG = Pattern.compile("【\\s*([^】]+?)\\s*】");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern TITLE_BRACKET_NOISE = Pattern.compile("[【\\[（(][^】\\]）)]*(?:高清|标清|超清|蓝光|4k|8k|1080p|2160p|hdr|sdr|fps|码率|臻彩|真彩|杜比|hifi|无损|内嵌|内封|字幕|中字|国配|中配|日配|粤语|原声|未删减|已完结|完结|全集|完整版|更新至|更至|更\\s*\\d+|\\d+\\s*集全|夸克|百度|迅雷|网盘|影视剧|资源)[^】\\]）)]*[】\\]）)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_ATTACHED_NOISE = Pattern.compile("(?i)(?:高清在线播放|超清在线播放|蓝光在线播放|在线免费观看|免费在线观看|高清免费观看|免费播放)\\s*$");
    private static final Pattern TITLE_TRAILING_NOISE = Pattern.compile("(?i)(?:[\\s_\\-—|｜:：·,，。/]+|^)(?:高清|超清|蓝光|4k|1080p|2160p|hd|bd|在线播放|在线免费观看|免费在线观看|免费观看|免费播放|已完结|完结|全集|全\\d+集|完整版|高清版|蓝光版|无删减(?:版)?|更新至第?\\s*\\d+\\s*集|更新至\\s*\\d+)\\s*$");
    private static final Pattern TITLE_CATEGORY_PREFIX = Pattern.compile("(?i)^\\s*(?:[#＃]\\s*)?(?:[\\p{So}\\p{Sk}]\\s*)*[【\\[（(]?\\s*(?:高清|4k|蓝光)?\\s*(?:国剧|国产剧|陆剧|大陆剧|港剧|台剧|美剧|英剧|日剧|韩剧|泰剧|电视剧|剧集|电影|动漫|动画|番剧|综艺|纪录片|短剧|网剧)\\s*[】\\]）)]?\\s*");
    private static final Pattern TITLE_UPDATE_PROGRESS = Pattern.compile("(?i)(?:[\\s._\\-—|｜:：]+|^)(?:更新至?|更至?|更)\\s*(?:第|EP)?\\s*0*\\d{1,4}\\s*(?:集|话|話|期)?(?=\\s|$|[【\\[（(,，。])");
    private static final Pattern TITLE_EPISODE_RANGE = Pattern.compile("(?i)(?:S\\d{1,2})?E\\d{1,4}\\s*[-—~～至到]+\\s*(?:S\\d{1,2})?E?\\d{1,4}");
    private static final Pattern TITLE_CAST_SUFFIX = Pattern.compile("\\s*[【\\[（(]\\s*[\\p{IsHan}·]{2,8}(?:[\\s、,，/]+[\\p{IsHan}·]{2,8}){1,7}\\s*[】\\]）)]\\s*$");
    private static final Pattern TITLE_CAST_TEXT_SUFFIX = Pattern.compile("\\s*[,，]\\s*[\\p{IsHan}·]{2,8}(?:[\\s、,，/]+[\\p{IsHan}·]{2,8}){1,7}\\s*$");
    private static final Pattern FILE_NOISE = Pattern.compile("(?i)\\b(?:480P|720P|1080P|2160P|4K|8K|UHD|WEB[ ._-]?DL|WEBRIP|BLURAY|BDRIP|HDTV|REMUX|DSNP|ATVP|AMZN|NETFLIX|NF|HMAX|PCOK|HDR10\\+?|HDR|DV|DOVI|DOLBY[ ._-]?VISION|H[ ._-]?26[45]|HEVC|AVC|X26[45]|AV1|10BIT|8BIT|AAC|AC3|EAC3|DDP?\\d(?:[ ._-]+\\d)?|DDP?|TRUEHD|ATMOS|DTS|CHS|CHT|JPN|ENG|REPACK|PROPER)\\b");
    private static final Pattern TARGET_FILE_NOISE = Pattern.compile("(?i)(?:\\b(?:\\d{3,4}p|[248]k|web[ ._-]?dl|webrip|blu[ ._-]?ray|bdrip|hdtv|remux|uhd|hdr10\\+?|hdr|dovi|dolby[ ._-]?vision|hevc|avc|av1|x?26[45]|h[ ._-]?26[45]|10bit|8bit|aac|ac3|eac3|ddp?|truehd|atmos|dts|repack|proper|\\d{2,3}fps)\\b|\\b\\d(?:[ ._-]+\\d){1,2}ch\\b|\\b\\d[ ._-]+\\d\\b)");
    private static final Pattern FILE_TITLE_EPISODE_BOUNDARY = Pattern.compile("(?i)(?:^|[. _\\-])(?:S\\d{1,2}E\\d{1,4}|E(?:P)?\\d{1,4}|第\\s*[一二三四五六七八九十两俩〇零0-9]+\\s*[集话話期])");
    private static final Pattern FILE_SEASON_EPISODE = Pattern.compile("(?i)S\\d{1,2}(?=E\\d{1,4})");
    private static final Pattern FILE_SIZE_SUFFIX = Pattern.compile("(?i)\\s*[【\\[（(]\\s*\\d+(?:\\.\\d+)?\\s*(?:KB|MB|GB|TB|K|M|G|T)\\s*[】\\]）)]\\s*$");
    private static final Pattern TITLE_METADATA_YEAR = Pattern.compile("[（(]\\s*(?:19|20)\\d{2}\\s*[）)]");
    private static final Pattern TITLE_SEASON_SUFFIX = Pattern.compile("(?i)\\s*(?:第?\\s*[一二三四五六七八九十两俩〇零0-9]{1,4}\\s*[季部]|S(?:eason)?\\s*0*\\d{1,2})\\s*$");
    private static final Pattern TITLE_ROMAN_SEASON_MARKER = Pattern.compile("(?i)((?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ])|(?<=[\\p{IsHan}])(?:VIII|VII|III|VI|IV|IX|II|V|X))(?=$|[\\s~～:：·\\-—])");
    private static final Pattern TITLE_SUBTITLE_SUFFIX = Pattern.compile("(?i)(?:[\\s_\\-—|｜:：·,，。/]+|^)(?:简体(?:中文)?字幕|繁体(?:中文)?字幕|简中字幕?|繁中字幕?|中文字幕|中英双语字幕|双语字幕|官方字幕|内嵌字幕|内封字幕|外挂字幕|无字幕|简体中字|繁体中字)\\s*$");
    private static final Pattern TITLE_RELEASE_SUFFIX = Pattern.compile("(?i)(?:[\\s_\\-—|｜:：·,，。/]+|^)(?:web|web[ ._-]?dl|webrip)[\\s_\\-—|｜:：·,，。/]*$");
    private static final Pattern TITLE_LANGUAGE_SUFFIX = Pattern.compile("(?i)\\s*[【\\[（(]?\\s*(?:中文(?:配音|版)?|中配|国语(?:版|配音)?|普通话(?:版|配音)?|国配|粤语(?:版|配音)?|粤配|日语(?:版|配音)?|日配|英语(?:版|配音)?|英配|韩语(?:版|配音)?|韩配|泰语(?:版|配音)?|泰配|双语(?:版|配音)?|配音版)\\s*[】\\]）)]?\\s*$");
    private static final Pattern TITLE_CONTENT_MARKER = Pattern.compile("(?i)(?:📜|💾|🏷|⬇|\\|\\s*🔍|(?:剧情)?介绍\\s*[:：])");
    private static final Pattern TITLE_YEAR_PREFIX = Pattern.compile("^(?:19|20)\\d{2}[\\s._\\-—]+");
    private static final Pattern TITLE_SERIAL_SUFFIX = Pattern.compile("\\s*年番\\s*\\d{0,2}\\s*$");
    private static final Pattern TITLE_DECORATION_PREFIX = Pattern.compile("^[\\p{So}\\p{Sk}#＃\\s]+");
    private static final Pattern TITLE_RELEASE_GROUP_PREFIX = Pattern.compile("(?i)^(?:[【\\[]\\s*[A-Z0-9][A-Z0-9._-]{0,11}\\s*[】\\]]|[A-Z0-9]{2,8}(?:WEB|TV|HD))\\s*");
    private static final Pattern TITLE_TECH_SUFFIX = Pattern.compile("(?i)(?:[\\s_\\-—|｜:：]+)(?:臻彩(?:MAX\\+?)?|真彩|\\d{2,3}FPS|高码率|杜比(?:全景声|视界|音效)?|FLAC(?:无损)?|HIFI声?|HDR|SDR|10BIT|WEB(?:[ ._-]?DL)?|内嵌|内封|外挂|简中|繁中)(?:[\\s_\\-—|｜:：&+/].*)?$");
    private static final Pattern TITLE_COLLECTION_DETAIL_SUFFIX = Pattern.compile("(?i)(?:[\\s._\\-—|｜:：]+|(?<=[季部]))(?:全\\s*\\d{1,4}\\s*[集话話期]|\\d{1,4}\\s*[集话話期]\\s*全)(?:\\s*.*)?$");
    private static final Pattern TITLE_BUNDLE_SUFFIX = Pattern.compile("(?i)\\s*(?:附|含|带)\\s*(?:第\\s*[一二三四五六七八九十0-9]+\\s*季|S\\s*0*\\d{1,2})(?:\\s*(?:全集|全\\d+集))?\\s*$");
    private static final Pattern TITLE_EDGE_SEPARATOR = Pattern.compile("^[\\s_\\-—|｜:：·,，。~～]+|[\\s_\\-—|｜:：·,，。~～]+$");
    private static final Pattern TITLE_SEASON_COLLECTION = Pattern.compile("(?i)(?:S\\s*0*\\d{1,2}\\s*[-—~～至到]+\\s*S?\\s*0*\\d{1,2}\\s*(?:季|全集)?|第\\s*[一二三四五六七八九十0-9]+\\s*[-—~～至到]+\\s*[一二三四五六七八九十0-9]+\\s*季)");
    private static final Pattern INVISIBLE_TEXT = Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]");
    private static final List<String> OFFICIAL_PLATFORMS = List.of("qiyi", "bilibili1", "imgo", "youku", "qq", "migu", "renren", "hanjutv", "bahamut", "dandan", "sohu", "leshi", "xigua", "maiduidui", "aiyifan", "animeko", "custom");
    private static final Map<String, String> SOURCE_ALIASES = Map.ofEntries(
            Map.entry("tencent", "qq"), Map.entry("qq", "qq"), Map.entry("腾讯", "qq"), Map.entry("腾讯视频", "qq"),
            Map.entry("iqiyi", "qiyi"), Map.entry("qiyi", "qiyi"), Map.entry("爱奇艺", "qiyi"),
            Map.entry("bilibili", "bilibili1"), Map.entry("bilibili1", "bilibili1"), Map.entry("bili", "bilibili1"), Map.entry("b站", "bilibili1"), Map.entry("哔哩哔哩", "bilibili1"),
            Map.entry("mango", "imgo"), Map.entry("mgtv", "imgo"), Map.entry("imgo", "imgo"), Map.entry("芒果", "imgo"), Map.entry("芒果tv", "imgo"),
            Map.entry("youku", "youku"), Map.entry("优酷", "youku"),
            Map.entry("migu", "migu"), Map.entry("miguvideo", "migu"), Map.entry("咪咕", "migu"),
            Map.entry("renren", "renren"), Map.entry("rr", "renren"), Map.entry("人人", "renren"), Map.entry("人人视频", "renren"), Map.entry("人人影视", "renren"),
            Map.entry("hanjutv", "hanjutv"), Map.entry("hanju", "hanjutv"), Map.entry("韩剧tv", "hanjutv"), Map.entry("韩剧", "hanjutv"),
            Map.entry("bahamut", "bahamut"), Map.entry("巴哈", "bahamut"), Map.entry("巴哈姆特", "bahamut"),
            Map.entry("dandan", "dandan"), Map.entry("dandanplay", "dandan"), Map.entry("弹弹play", "dandan"), Map.entry("弹弹", "dandan"),
            Map.entry("sohu", "sohu"), Map.entry("搜狐", "sohu"),
            Map.entry("leshi", "leshi"), Map.entry("letv", "leshi"), Map.entry("乐视", "leshi"),
            Map.entry("xigua", "xigua"), Map.entry("ixigua", "xigua"), Map.entry("douyin", "xigua"), Map.entry("西瓜", "xigua"), Map.entry("西瓜视频", "xigua"),
            Map.entry("maiduidui", "maiduidui"), Map.entry("mdd", "maiduidui"), Map.entry("埋堆堆", "maiduidui"),
            Map.entry("aiyifan", "aiyifan"), Map.entry("iyf", "aiyifan"), Map.entry("爱壹帆", "aiyifan"), Map.entry("爱一帆", "aiyifan"),
            Map.entry("animeko", "animeko"), Map.entry("custom", "custom"));

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && (DanmakuSetting.hasValidApiUrl() || hasBuiltinProviders());
    }

    private static boolean hasBuiltinProviders() {
        return !TextUtils.isEmpty(normalizeBaseUrl(BuildConfig.DANMAKU_PIZAZZ_BASE)) || !TextUtils.isEmpty(normalizeBaseUrl(BuildConfig.DANMAKU_UZDM_BASE));
    }


    public static boolean canAutoSearch(List<Danmaku> siteDanmakus) {
        return canSearch() && (!DanmakuSetting.isSpiderFirst() || siteDanmakus == null || siteDanmakus.isEmpty());
    }

    public static Call newCall(String name, String episode) {
        String url = DanmakuSetting.getValidApiUrl();
        if (TextUtils.isEmpty(url)) url = DanmakuSetting.getEffectiveApiUrl();
        if (TextUtils.isEmpty(url)) return null;
        OkHttp.cancel(TAG);
        name = Trans.t2s(false, name == null ? "" : name);
        episode = Trans.t2s(false, episode == null ? "" : episode);
        try {
            if (url.contains("{name}") || url.contains("{episode}")) {
                return OkHttp.newCall(url.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)), TAG);
            }
            String base = normalizeBaseUrl(url);
            if (!TextUtils.isEmpty(base) && !url.contains("?") && !url.contains("{")) {
                // dandan-like base: use episode search for manual dialog keyword
                String keyword = TextUtils.isEmpty(name) ? episode : name;
                return OkHttp.newCall(base + API_SEARCH_EPISODES + "?anime=" + Uri.encode(keyword), TAG);
            }
            url = getSearchUrl(url);
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("name", name);
            params.put("episode", episode);
            return OkHttp.newCall(url, OkHttp.toBody(params), TAG);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getSearchUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Uri uri = Uri.parse(url);
        List<String> segments = uri.getPathSegments();
        if (!segments.isEmpty() && "danmaku".equalsIgnoreCase(segments.get(segments.size() - 1))) return url;
        if (segments.size() > 1) return url;
        return uri.buildUpon().appendPath("danmaku").build().toString();
    }

    public static List<Danmaku> arrayFrom(String body) {
        return normalize(Danmaku.arrayFrom(body));
    }

    private static List<Danmaku> normalize(List<Danmaku> items) {
        if (items == null || items.isEmpty()) return items == null ? List.of() : items;
        String api = getSearchUrl(DanmakuSetting.getValidApiUrl());
        if (TextUtils.isEmpty(api)) api = normalizeBaseUrl(DanmakuSetting.getEffectiveApiUrl());
        for (Danmaku item : items) {
            if (!TextUtils.isEmpty(item.getUrl())) item.setUrl(normalizeResultUrl(api, item.getUrl()));
        }
        return items;
    }

    private static String normalizeResultUrl(String api, String url) {
        try {
            return com.fongmi.android.tv.player.danmaku.DanmakuUrlPolicy.normalize(api, url);
        } catch (Throwable ignored) {
            return url;
        }
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        // Enhanced matching path from snow-movie (title clean / score / multi-provider).
        searchAuto(name, episode, "", null, item -> {
            if (item == null || item.isEmpty()) return;
            String api = getSearchUrl(DanmakuSetting.getValidApiUrl());
            if (TextUtils.isEmpty(api)) api = normalizeBaseUrl(DanmakuSetting.getEffectiveApiUrl());
            if (!TextUtils.isEmpty(item.getUrl())) item.setUrl(normalizeResultUrl(api, item.getUrl()));
            found.accept(item);
        });
    }


    public static Call newAnimeSearchCall(String keyword) {
        OkHttp.cancel(TAG);
        String url = normalizeBaseUrl(DanmakuSetting.getEffectiveApiUrl()) + API_SEARCH_ANIME + "?keyword=" + Uri.encode(Trans.t2s(false, keyword == null ? "" : keyword).trim());
        return OkHttp.newCall(url, TAG);
    }

    public static Call newBangumiCall(Danmaku anime) {
        OkHttp.cancel(TAG);
        String id = anime == null ? "" : anime.getBangumiId();
        String url = normalizeBaseUrl(DanmakuSetting.getEffectiveApiUrl()) + API_BANGUMI + Uri.encode(id);
        return OkHttp.newCall(url, TAG);
    }

    public static Call newEpisodeSearchCall(Danmaku anime) {
        OkHttp.cancel(TAG);
        String title = anime == null ? "" : cleanAnimeSearchTitle(anime.getAnimeTitle());
        String url = buildEpisodeSearchUrl(DanmakuSetting.getEffectiveApiUrl(), Trans.t2s(false, title).trim(), "");
        return OkHttp.newCall(url, TAG);
    }

    public static void searchAuto(String name, String episode, String sourceHint, Vod vod, Consumer<Danmaku> found) {
        AutoTarget target = AutoTarget.create(name, episode, sourceHint, vod);
        Log.i(TAG, "auto target title=" + target.title + " season=" + target.season + " episode=" + target.episode + " type=" + target.type + " total=" + target.total + " file=" + target.rawFileName);
        String cacheKey = target.title + "#" + target.season + "#" + target.episode + "#" + target.year + "#" + target.type + "#" + target.platform;
        AutoCache cached = AUTO_CACHE.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.time < 1800000L) {
            App.post(() -> found.accept(cached.item));
            return;
        }
        Consumer<Danmaku> cachedFound = item -> {
            AUTO_CACHE.put(cacheKey, new AutoCache(item));
            if (AUTO_CACHE.size() > 256) AUTO_CACHE.clear();
            found.accept(item);
        };
        if (DanmakuSetting.canUseAi()) {
            cleanWithAi(target, cleaned -> searchWithMetadata(cleaned, cachedFound), e -> searchWithMetadata(target, cachedFound));
            return;
        }
        searchWithMetadata(target, cachedFound);
    }

    private static void searchWithMetadata(AutoTarget target, Consumer<Danmaku> found) {
        String token = DanmakuSetting.getTmdbToken();
        if (TextUtils.isEmpty(token) || TextUtils.isEmpty(target.title)) {
            searchOfficial(target, found);
            return;
        }
        String url = TMDB_SEARCH + "?query=" + Uri.encode(target.title) + "&language=zh-CN&include_adult=false";
        OkHttp.client(3500).newCall(new Request.Builder().url(url).header("Authorization", "Bearer " + token).build()).enqueue(new Callback() {
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                AutoTarget enriched = target;
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    if (response.isSuccessful()) {
                        JSONArray results = new JSONObject(body).optJSONArray("results");
                        if (results != null) for (int i = 0; i < Math.min(results.length(), 10); i++) {
                            JSONObject item = results.optJSONObject(i);
                            if (item == null || !("tv".equals(item.optString("media_type")) || "movie".equals(item.optString("media_type")))) continue;
                            String title = firstNonEmpty(item.optString("name"), item.optString("title"));
                            if (!TextUtils.isEmpty(title)) { enriched = target.withTitle(title); break; }
                        }
                    }
                } catch (Exception e) { Log.w(TAG, "tmdb enrich failed", e); }
                searchOfficial(enriched, found);
            }
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { searchOfficial(target, found); }
        });
    }

    private static void searchOfficial(AutoTarget target, Consumer<Danmaku> found) {
        new AutoRace(target, found).start();
    }

    private static final class AutoRace {
        private final AutoTarget target;
        private final Consumer<Danmaku> found;
        private final List<Danmaku> candidates = Collections.synchronizedList(new ArrayList<>());
        private final AtomicBoolean delivered = new AtomicBoolean();
        private final AtomicBoolean probing = new AtomicBoolean();
        private List<String> bases;

        AutoRace(AutoTarget target, Consumer<Danmaku> found) {
            this.target = target;
            this.found = found;
        }

        void start() {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            String configured = normalizeBaseUrl(DanmakuSetting.getEffectiveApiUrl());
            if (!TextUtils.isEmpty(configured)) values.add(configured);
            addBase(values, BuildConfig.DANMAKU_PIZAZZ_BASE);
            addBase(values, BuildConfig.DANMAKU_UZDM_BASE);
            bases = new ArrayList<>(values);
            requestMatches();
        }

        private void addBase(LinkedHashSet<String> values, String base) {
            String normalized = normalizeBaseUrl(base);
            if (!TextUtils.isEmpty(normalized)) values.add(normalized);
        }

        private void requestMatches() {
            String fileName = buildMatchFileName(target);
            AtomicInteger pending = new AtomicInteger(bases.size());
            Log.i(TAG, "auto race match fileName=" + fileName + " providers=" + bases.size());
            for (String base : bases) {
                String cacheKey = "match#" + base + "#" + fileName;
                String cached = cachedBody(cacheKey);
                if (cached != null) {
                    try { candidates.addAll(parseMatchResult(cached, target, base)); } catch (Exception ignored) {}
                    finishMatch(pending);
                    continue;
                }
                if (providerBlocked(base)) { finishMatch(pending); continue; }
                newMatchCall(base, fileName, TAG + "_race").enqueue(new Callback() {
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                        try {
                            String body = response.body() == null ? "" : response.body().string();
                            if (response.isSuccessful()) { cacheBody(cacheKey, body, 1800000L); providerSuccess(base); candidates.addAll(parseMatchResult(body, target, base)); }
                            else providerFailure(base);
                        } catch (Exception e) { providerFailure(base); Log.w(TAG, "race match parse failed base=" + base, e); }
                        finishMatch(pending);
                    }
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { providerFailure(base); finishMatch(pending); }
                });
            }
            App.post(() -> { if (pending.get() > 0 && hasStrongCandidate()) probeCounts(); }, 3500);
        }

        private void finishMatch(AtomicInteger pending) {
            if (pending.decrementAndGet() == 0) {
                if (hasStrongCandidate()) probeCounts();
                else requestFallbacks();
            }
        }

        private void requestFallbacks() {
            List<String> titles = fallbackTitles(target);
            AtomicInteger pending = new AtomicInteger(bases.size() * titles.size());
            for (String base : bases) {
                for (String title : titles) {
                    String url = buildEpisodeSearchUrl(base, title, target.episode);
                    String cacheKey = "episodes#" + url;
                    String cached = cachedBody(cacheKey);
                    if (cached != null) {
                        try { candidates.addAll(parseSearchResult(cached, target.sourceHint, target.episode, target, base)); } catch (Exception ignored) {}
                        finishFallback(pending);
                        continue;
                    }
                    if (providerBlocked(base)) { finishFallback(pending); continue; }
                    OkHttp.newCall(url, TAG + "_race").enqueue(new Callback() {
                        @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                            try {
                                String body = response.body() == null ? "" : response.body().string();
                                if (response.isSuccessful()) { cacheBody(cacheKey, body, 3600000L); providerSuccess(base); candidates.addAll(parseSearchResult(body, target.sourceHint, target.episode, target, base)); }
                                else providerFailure(base);
                            } catch (Exception e) { providerFailure(base); Log.w(TAG, "race fallback parse failed base=" + base, e); }
                            finishFallback(pending);
                        }
                        @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { providerFailure(base); finishFallback(pending); }
                    });
                }
            }
            App.post(() -> { if (pending.get() > 0 && !candidates.isEmpty()) probeCounts(); }, 6000);
        }

        private static List<String> fallbackTitles(AutoTarget target) {
            LinkedHashSet<String> titles = new LinkedHashSet<>();
            addSearchTitle(titles, target.searchTitle);
            addSearchTitleVariants(titles, target.searchTitle);
            addSearchTitle(titles, target.title);
            addSearchTitleVariants(titles, target.title);
            String fileTitle = extractFileSearchTitle(target.rawFileName);
            if (isUsableFileTitle(fileTitle)) {
                addSearchTitle(titles, fileTitle);
                addSearchTitleVariants(titles, fileTitle);
            }
            for (String alias : aliasesFor(target.title)) addSearchTitle(titles, alias);
            for (String alias : aliasesFor(target.searchTitle)) addSearchTitle(titles, alias);
            if (!TextUtils.isEmpty(target.season)) {
                List<String> current = new ArrayList<>(titles);
                for (String title : current) addSearchTitle(titles, title + " 第" + target.season + "季");
            }
            return new ArrayList<>(titles);
        }

        private static void addSearchTitle(LinkedHashSet<String> titles, String title) {
            String value = cleanAnimeSearchTitle(title);
            if (!TextUtils.isEmpty(value) && !isPlaceholderTitle(value)) titles.add(value);
        }

        private static void addSearchTitleVariants(LinkedHashSet<String> titles, String title) {
            String value = cleanAnimeSearchTitle(title);
            for (String separator : List.of("：", ":", "～", "~")) {
                int index = value.indexOf(separator);
                if (index >= 2) addSearchTitle(titles, value.substring(0, index));
            }
        }

        private void finishFallback(AtomicInteger pending) {
            if (pending.decrementAndGet() == 0) probeCounts();
        }

        private boolean hasStrongCandidate() {
            synchronized (candidates) {
                for (Danmaku item : candidates) if (scoreMatch(target, item) >= 110) return true;
            }
            return false;
        }

        private void probeCounts() {
            if (delivered.get() || !probing.compareAndSet(false, true)) return;
            List<Danmaku> valid = new ArrayList<>();
            synchronized (candidates) {
                for (Danmaku item : candidates) {
                    int score = scoreMatch(target, item);
                    if (score != Integer.MIN_VALUE) { item.setMatchScore(score); valid.add(item); }
                }
            }
            if (valid.isEmpty()) return;
            valid.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));
            List<Danmaku> probe = valid.subList(0, Math.min(4, valid.size()));
            AtomicInteger pending = new AtomicInteger(probe.size());
            for (Danmaku item : probe) {
                String url = item.getUrl().replace("format=xml", "format=json");
                String countCacheKey = "count#" + url;
                String cached = cachedBody(countCacheKey);
                if (cached != null) {
                    try { item.setCommentCount(Integer.parseInt(cached)); } catch (Exception ignored) {}
                    if (pending.decrementAndGet() == 0) deliverBest();
                    continue;
                }
                OkHttp.newCall(url, TAG + "_count").enqueue(new Callback() {
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                        try {
                            String head = response.peekBody(8192).string();
                            Matcher matcher = Pattern.compile("\\\"count\\\"\\s*:\\s*(\\d+)").matcher(head);
                            if (matcher.find()) { item.setCommentCount(Integer.parseInt(matcher.group(1))); cacheBody(countCacheKey, matcher.group(1), 1800000L); }
                        } catch (Exception ignored) {}
                        if (pending.decrementAndGet() == 0) deliverBest();
                    }
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { if (pending.decrementAndGet() == 0) deliverBest(); }
                });
            }
            App.post(this::deliverBest, 2500);
        }

        private void deliverBest() {
            if (!delivered.compareAndSet(false, true)) return;
            Danmaku best = null; int bestScore = Integer.MIN_VALUE;
            synchronized (candidates) {
                for (Danmaku item : candidates) {
                    int base = scoreMatch(target, item);
                    if (base == Integer.MIN_VALUE) continue;
                    int count = item.getCommentCount();
                    int quality = count < 0 ? 0 : count == 0 ? -30 : count < 50 ? -20 : count < 200 ? -10 : count < 1000 ? 5 : count < 5000 ? 15 : count < 10000 ? 25 : 35;
                    int score = base + quality;
                    item.setMatchScore(score);
                    if (score > bestScore) { bestScore = score; best = item; }
                }
            }
            if (best != null) {
                Danmaku selected = best;
                Log.i(TAG, "auto race selected=" + selected.getAnimeTitle() + " episode=" + selected.getEpisodeTitle() + " count=" + selected.getCommentCount() + " score=" + selected.getMatchScore());
                App.post(() -> found.accept(selected));
            }
        }
    }

    public static void fetchAiModels(Consumer<List<String>> success, Consumer<Exception> error) {
        OkHttp.cancel(TAG_AI);
        String apiKey = DanmakuSetting.getAiApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            App.post(() -> error.accept(new IllegalStateException("AI API Key is empty")));
            return;
        }
        OkHttp.newCall(buildAiModelsUrl(), Map.of("Authorization", "Bearer " + apiKey)).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    String body = response.body().string();
                    List<String> models = parseAiModels(body);
                    Log.i(TAG_AI, "models response code=" + response.code() + " count=" + models.size());
                    App.post(() -> success.accept(models));
                } catch (Exception e) {
                    App.post(() -> error.accept(e));
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                App.post(() -> error.accept(e));
            }
        });
    }

    public static void testAiMatch(String name, String episode, String sourceHint, Consumer<AiMatchTestResult> success, Consumer<Exception> error) {
        OkHttp.cancel(TAG_AI);
        if (!DanmakuSetting.hasAiApiKey()) {
            App.post(() -> error.accept(new IllegalStateException("AI API Key is empty")));
            return;
        }
        try {
            AutoTarget target = AutoTarget.createForTest(name, episode, sourceHint);
            long startedAt = System.currentTimeMillis();
            cleanWithAiDetailed(target, data -> {
                AiMatchTestResult result = new AiMatchTestResult(data.input, buildMatchFileName(data.target), data.rawResponse, List.of(), System.currentTimeMillis() - startedAt);
                App.post(() -> success.accept(result));
            }, error);
        } catch (Exception e) {
            App.post(() -> error.accept(e));
        }
    }

    public static void syncAiConfig() {
        // AI配置保存在本机，匹配时直接调用配置的OpenAI兼容接口。
    }

    public static List<Danmaku> parseSearchResult(String body) throws Exception {
        return parseSearchResult(body, "");
    }

    public static List<Danmaku> parseSearchResult(String body, String sourceHint) throws Exception {
        return parseSearchResult(body, sourceHint, "");
    }

    public static List<Danmaku> parseSearchResult(String body, String sourceHint, String episodeNumber) throws Exception {
        return parseSearchResult(body, sourceHint, episodeNumber, null);
    }

    public static List<Danmaku> parseAnimeSearchResult(String body) throws Exception {
        String text = body == null ? "" : body.trim();
        if (!text.startsWith("{")) return List.of();
        JSONObject object = new JSONObject(text);
        JSONArray animes = object.optJSONArray("animes");
        if (animes == null) return List.of();
        List<Danmaku> items = new ArrayList<>();
        for (int i = 0; i < animes.length(); i++) {
            JSONObject anime = animes.optJSONObject(i);
            if (anime == null) continue;
            Danmaku danmaku = new Danmaku();
            String animeTitle = anime.optString("animeTitle");
            String source = resolveSource(anime.optString("source"), animeTitle, "", "");
            danmaku.setName(buildAnimeName(anime));
            danmaku.setAnimeTitle(animeTitle);
            danmaku.setAnimeId(anime.optString("animeId"));
            danmaku.setBangumiId(firstNonEmpty(anime.optString("bangumiId"), danmaku.getAnimeId()));
            danmaku.setSource(source);
            danmaku.setType(firstNonEmpty(anime.optString("typeDescription"), anime.optString("type")));
            danmaku.setEpisodeCount(anime.optInt("episodeCount", 0));
            danmaku.setMatchType(Danmaku.MATCH_MANUAL);
            if (!TextUtils.isEmpty(danmaku.getBangumiId()) && !"0".equals(danmaku.getBangumiId())) items.add(danmaku);
        }
        return items;
    }

    public static List<Danmaku> parseBangumiEpisodes(String body, Danmaku anime) throws Exception {
        String text = body == null ? "" : body.trim();
        if (!text.startsWith("{")) return List.of();
        JSONObject object = new JSONObject(text);
        JSONObject bangumi = object.optJSONObject("bangumi");
        if (bangumi == null) return List.of();
        JSONArray episodes = bangumi.optJSONArray("episodes");
        if (episodes == null) return List.of();
        String animeTitle = firstNonEmpty(bangumi.optString("animeTitle"), anime == null ? "" : anime.getAnimeTitle());
        List<Danmaku> items = new ArrayList<>();
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject episode = episodes.optJSONObject(i);
            if (episode == null) continue;
            long episodeId = episode.optLong("episodeId", 0);
            if (episodeId <= 0) continue;
            String episodeTitle = episode.optString("episodeTitle");
            String source = anime == null ? resolveSource(bangumi.optString("source"), animeTitle, "", "") : resolveSource(anime.getSource(), anime.getAnimeTitle(), "", sourceFromTitle(animeTitle));
            Danmaku danmaku = new Danmaku();
            danmaku.setName(buildName(animeTitle, episodeTitle));
            danmaku.setUrl(buildCommentUrl(episodeId));
            danmaku.setMatchType(Danmaku.MATCH_MANUAL);
            danmaku.setEpisodeTitle(episodeTitle);
            if (anime != null) {
                danmaku.setAnimeId(anime.getAnimeId());
                danmaku.setBangumiId(anime.getBangumiId());
                danmaku.setAnimeTitle(anime.getAnimeTitle());
                danmaku.setSource(source);
                danmaku.setType(anime.getType());
            }
            items.add(danmaku);
        }
        return items;
    }

    public static List<Danmaku> parseEpisodeSearchResult(String body, Danmaku selectedAnime) throws Exception {
        String text = body == null ? "" : body.trim();
        if (!text.startsWith("{")) return List.of();
        JSONObject object = new JSONObject(text);
        JSONArray animes = object.optJSONArray("animes");
        if (animes == null) return List.of();
        List<Danmaku> items = new ArrayList<>();
        for (int i = 0; i < animes.length(); i++) {
            JSONObject anime = animes.optJSONObject(i);
            if (anime == null || !isSelectedAnime(anime, selectedAnime)) continue;
            String animeTitle = anime.optString("animeTitle");
            String source = resolveSource(anime.optString("source"), animeTitle, "", "");
            JSONArray episodes = anime.optJSONArray("episodes");
            if (episodes == null) continue;
            for (int j = 0; j < episodes.length(); j++) {
                JSONObject episode = episodes.optJSONObject(j);
                if (episode == null) continue;
                long episodeId = episode.optLong("episodeId", 0);
                if (episodeId <= 0) continue;
                String episodeTitle = episode.optString("episodeTitle");
                Danmaku danmaku = new Danmaku();
                danmaku.setName(buildName(animeTitle, episodeTitle));
                danmaku.setUrl(buildCommentUrl(episodeId));
                danmaku.setMatchType(Danmaku.MATCH_MANUAL);
                danmaku.setEpisodeTitle(episodeTitle);
                if (selectedAnime != null) {
                    danmaku.setAnimeId(selectedAnime.getAnimeId());
                    danmaku.setBangumiId(selectedAnime.getBangumiId());
                    danmaku.setAnimeTitle(selectedAnime.getAnimeTitle());
                    danmaku.setSource(firstNonEmpty(selectedAnime.getSource(), source));
                    danmaku.setType(selectedAnime.getType());
                }
                items.add(danmaku);
            }
        }
        return items;
    }

    private static List<Danmaku> parseSearchResult(String body, String sourceHint, String episodeNumber, AutoTarget target) throws Exception {
        return parseSearchResult(body, sourceHint, episodeNumber, target, normalizeBaseUrl(DanmakuSetting.getEffectiveApiUrl()));
    }

    private static List<Danmaku> parseSearchResult(String body, String sourceHint, String episodeNumber, AutoTarget target, String baseUrl) throws Exception {
        String text = body == null ? "" : body.trim();
        if (text.startsWith("[")) return markMatchType(Danmaku.arrayFrom(text), Danmaku.MATCH_DIRECT);
        if (!text.startsWith("{")) return List.of();
        JSONObject object = new JSONObject(text);
        JSONArray animes = object.optJSONArray("animes");
        if (animes == null) return List.of();
        String targetEpisode = normalizeEpisode(episodeNumber);
        List<Danmaku> preferred = new ArrayList<>();
        List<Danmaku> others = new ArrayList<>();
        for (int i = 0; i < animes.length(); i++) {
            JSONObject anime = animes.optJSONObject(i);
            if (anime == null) continue;
            String animeTitle = anime.optString("animeTitle");
            if (target != null && !isValidAnime(target, animeTitle, anime.optString("type"), anime.optString("typeDescription"))) continue;
            JSONArray episodes = anime.optJSONArray("episodes");
            if (episodes == null) continue;
            for (int j = 0; j < episodes.length(); j++) {
                JSONObject episode = episodes.optJSONObject(j);
                if (episode == null) continue;
                String episodeTitle = episode.optString("episodeTitle");
                if (target != null && isUnexpectedExtraEpisode(target, episodeTitle)) continue;
                if (target != null ? !matchesTargetEpisode(target, episodeTitle) : !matchesEpisode(targetEpisode, episodeTitle)) continue;
                long episodeId = episode.optLong("episodeId", 0);
                if (episodeId <= 0) continue;
                String source = resolveSource(anime.optString("source"), animeTitle, episodeTitle, "");
                Danmaku danmaku = new Danmaku();
                danmaku.setName(buildName(animeTitle, episodeTitle));
                danmaku.setUrl(buildCommentUrl(baseUrl, episodeId));
                danmaku.setMatchType(Danmaku.MATCH_DIRECT);
                danmaku.setEpisodeTitle(episodeTitle);
                danmaku.setAnimeId(anime.optString("animeId"));
                danmaku.setBangumiId(firstNonEmpty(anime.optString("bangumiId"), danmaku.getAnimeId()));
                danmaku.setAnimeTitle(animeTitle);
                danmaku.setSource(source);
                danmaku.setType(firstNonEmpty(anime.optString("typeDescription"), anime.optString("type")));
                danmaku.setEpisodeCount(anime.optInt("episodeCount", 0));
                if (isPreferredSource(target, sourceHint, source, animeTitle)) preferred.add(danmaku);
                else others.add(danmaku);
            }
        }
        List<Danmaku> items = new ArrayList<>(preferred);
        items.addAll(others);
        return items;
    }

    private static List<Danmaku> parseMatchResult(String body, AutoTarget target) throws Exception {
        return parseMatchResult(body, target, normalizeBaseUrl(DanmakuSetting.getEffectiveApiUrl()));
    }

    private static List<Danmaku> parseMatchResult(String body, AutoTarget target, String baseUrl) throws Exception {
        String text = body == null ? "" : body.trim();
        if (!text.startsWith("{")) return List.of();
        JSONObject object = new JSONObject(text);
        JSONArray matches = object.optJSONArray("matches");
        if (matches == null || matches.length() == 0) return List.of();
        List<Danmaku> items = new ArrayList<>();
        String matchType = parseMatchType(object);
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.optJSONObject(i);
            if (match == null) continue;
            long episodeId = match.optLong("episodeId", 0);
            if (episodeId <= 0) continue;
            String animeTitle = match.optString("animeTitle");
            String episodeTitle = match.optString("episodeTitle");
            if (isUnexpectedExtraEpisode(target, episodeTitle)) continue;
            Danmaku danmaku = new Danmaku();
            danmaku.setName(buildName(animeTitle, episodeTitle));
            danmaku.setUrl(buildCommentUrl(baseUrl, episodeId));
            danmaku.setMatchType(matchType);
            danmaku.setEpisodeTitle(episodeTitle);
            danmaku.setAnimeId(match.optString("animeId"));
            danmaku.setBangumiId(firstNonEmpty(match.optString("bangumiId"), danmaku.getAnimeId()));
            danmaku.setAnimeTitle(animeTitle);
            danmaku.setSource(resolveSource(match.optString("source"), animeTitle, episodeTitle, target == null ? "" : target.platform));
            danmaku.setType(firstNonEmpty(match.optString("typeDescription"), match.optString("type")));
            danmaku.setEpisodeCount(match.optInt("episodeCount", 0));
            items.add(danmaku);
        }
        return items;
    }

    private static List<Danmaku> markMatchType(List<Danmaku> items, String matchType) {
        for (Danmaku item : items) {
            item.setMatchType(matchType);
            item.setSource(resolveSource(item.getSource(), item.getAnimeTitle(), firstNonEmpty(item.getEpisodeTitle(), item.getName()), ""));
        }
        return items;
    }

    private static String parseMatchType(JSONObject object) {
        String value = (object.optString("matchType") + " " + object.optString("matchMode") + " " + object.optString("matcher") + " " + object.optString("provider")).toLowerCase(Locale.ROOT);
        if (value.contains("ai") || value.contains("llm") || value.contains("gpt") || value.contains("kimi")) return Danmaku.MATCH_AI;
        if (value.contains("direct") || value.contains("rule") || value.contains("exact")) return Danmaku.MATCH_DIRECT;
        return Danmaku.MATCH_AUTO;
    }

    private static int scoreMatch(AutoTarget target, Danmaku item) {
        String episodeTitle = firstNonEmpty(item.getEpisodeTitle(), item.getName());
        if (isUnexpectedExtraEpisode(target, episodeTitle)) return Integer.MIN_VALUE;
        if (!matchesTargetEpisode(target, episodeTitle)) return Integer.MIN_VALUE;
        if (!matchesTargetTitle(target, item.getAnimeTitle())) return Integer.MIN_VALUE;
        if (!matchesYear(target.year, item.getAnimeTitle())) return Integer.MIN_VALUE;
        if (!matchesType(target.type, item.getType() + " " + item.getAnimeTitle())) return Integer.MIN_VALUE;
        String candidateSeason = extractCandidateSeason(item.getAnimeTitle());
        if (!TextUtils.isEmpty(target.season) && !TextUtils.isEmpty(candidateSeason) && !target.season.equals(candidateSeason)) return Integer.MIN_VALUE;
        if (parseInt(target.season, 0) > 1 && TextUtils.isEmpty(candidateSeason) && !isNamedSeasonTarget(target, item.getAnimeTitle())) return Integer.MIN_VALUE;
        int score = 0;
        if (!TextUtils.isEmpty(target.episode) && matchesEpisode(target.episode, episodeTitle)) score += 100;
        if (!TextUtils.isEmpty(target.season) && target.season.equals(candidateSeason)) score += 40;
        else if (!TextUtils.isEmpty(target.season) && isNamedSeasonTarget(target, item.getAnimeTitle())) score += 25;
        else if (!TextUtils.isEmpty(target.season) && TextUtils.isEmpty(candidateSeason)) score -= 15;
        if (!target.episodeTokens.isEmpty()) score += target.episodeTokens.size() * 40;
        if (!target.titleTokens.isEmpty()) {
            List<String> candidateTokens = episodeVariantTokens(item.getAnimeTitle() + " " + episodeTitle);
            for (String token : target.titleTokens) if (candidateTokens.contains(token)) score += 15;
        }
        if (isPreferredSource(target, target.sourceHint, item.getSource(), item.getAnimeTitle())) score += 20;
        if (matchesTargetTitle(target, item.getAnimeTitle())) score += 10;
        return score;
    }

    private static List<String> parseAiModels(String body) throws Exception {
        String text = body == null ? "" : body.trim();
        if (!text.startsWith("{")) return List.of();
        JSONObject object = new JSONObject(text);
        JSONArray data = object.optJSONArray("data");
        if (data == null) return List.of();
        List<String> models = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id");
            if (!TextUtils.isEmpty(id)) models.add(id);
        }
        return models;
    }

    private static Call newMatchCall(String baseUrl, String fileName, String tag) {
        JSONObject object = new JSONObject();
        try {
            object.put("fileName", fileName);
        } catch (Exception ignored) {
        }
        RequestBody body = RequestBody.create(object.toString().getBytes(StandardCharsets.UTF_8), JSON);
        return OkHttp.newCall(normalizeBaseUrl(baseUrl) + API_MATCH, body, tag);
    }

    private static void cleanWithAi(AutoTarget target, Consumer<AutoTarget> success, Consumer<Exception> error) {
        cleanWithAiDetailed(target, result -> success.accept(result.target), error);
    }

    private static void cleanWithAiDetailed(AutoTarget target, Consumer<AiCleanResult> success, Consumer<Exception> error) {
        try {
            JSONObject input = new JSONObject(buildAiInput(target));
            JSONObject request = new JSONObject();
            request.put("model", DanmakuSetting.getAiModel());
            request.put("temperature", 0);
            request.put("response_format", new JSONObject().put("type", "json_object"));
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", "你是影视文件名清洗器。只返回JSON对象，字段title、season、episode、year、media_type、confidence。media_type只能是tv/movie/anime/variety/unknown。不允许编造TMDB ID或弹幕ID；不确定字段返回null；不能把分辨率、编码、总集数或年份当成当前集数；未知季度不能默认第一季。"));
            messages.put(new JSONObject().put("role", "user").put("content", input.toString()));
            request.put("messages", messages);
            RequestBody body = RequestBody.create(request.toString().getBytes(StandardCharsets.UTF_8), JSON);
            String base = DanmakuSetting.getAiBaseUrl().replaceAll("/+$", "");
            String url = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
            String apiKey = DanmakuSetting.getAiApiKey().replaceFirst("(?i)^Bearer\\s+", "").trim();
            Request httpRequest = new Request.Builder().url(url).header("Authorization", "Bearer " + apiKey).post(body).tag(TAG_AI).build();
            AtomicBoolean completed = new AtomicBoolean();
            Call call = OkHttp.client(12000).newCall(httpRequest);
            Runnable timeout = () -> {
                if (!completed.compareAndSet(false, true)) return;
                call.cancel();
                error.accept(new IOException("AI request timed out after 15 seconds: " + url));
            };
            App.post(timeout, 15000);
            Log.i(TAG_AI, "chat request url=" + url + " model=" + DanmakuSetting.getAiModel());
            call.enqueue(new Callback() {
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String text = response.body() == null ? "" : response.body().string();
                        if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + ": " + text);
                        String content = new JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
                        if (content.startsWith("```")) content = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
                        JSONObject result = new JSONObject(content);
                        double confidence = result.optDouble("confidence", 0);
                        String title = result.optString("title").trim();
                        AutoTarget cleaned = confidence >= 0.6 && !TextUtils.isEmpty(title) ? target.withAi(result) : target;
                        if (!completed.compareAndSet(false, true)) return;
                        App.removeCallbacks(timeout);
                        App.post(() -> success.accept(new AiCleanResult(cleaned, input.toString(), text)));
                    } catch (Exception e) {
                        if (!completed.compareAndSet(false, true)) return;
                        App.removeCallbacks(timeout);
                        App.post(() -> error.accept(e));
                    }
                }
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (!completed.compareAndSet(false, true)) return;
                    App.removeCallbacks(timeout);
                    App.post(() -> error.accept(e));
                }
            });
        } catch (Exception e) {
            App.post(() -> error.accept(e));
        }
    }

    private static boolean isValidAnime(AutoTarget target, String animeTitle, String type, String typeDescription) {
        if (!matchesTargetTitle(target, animeTitle)) {
            Log.w(TAG, "reject danmaku title mismatch target=" + target.title + " candidate=" + animeTitle);
            return false;
        }
        if (!matchesYear(target.year, animeTitle)) {
            Log.w(TAG, "reject danmaku year mismatch target=" + target.year + " candidate=" + animeTitle);
            return false;
        }
        if (!matchesType(target.type, type + " " + typeDescription + " " + animeTitle)) {
            Log.w(TAG, "reject danmaku type mismatch target=" + target.type + " candidate=" + type + " " + typeDescription + " " + animeTitle);
            return false;
        }
        String candidateSeason = extractCandidateSeason(animeTitle);
        if (!TextUtils.isEmpty(target.season) && !TextUtils.isEmpty(candidateSeason) && !target.season.equals(candidateSeason)) {
            Log.w(TAG, "reject danmaku season mismatch target=" + target.season + " candidate=" + candidateSeason + " " + animeTitle);
            return false;
        }
        if (parseInt(target.season, 0) > 1 && TextUtils.isEmpty(candidateSeason) && !isNamedSeasonTarget(target, animeTitle)) return false;
        return true;
    }

    private static boolean matchesEpisode(String targetEpisode, String title) {
        if (TextUtils.isEmpty(targetEpisode)) return true;
        String episode = normalizeEpisode(title);
        String target = normalizeEpisode(targetEpisode);
        if (target.equals(episode)) return true;
        List<String> tokens = episodeVariantTokens(targetEpisode);
        if (!tokens.isEmpty()) return containsAllVariantTokens(title, tokens) && (!isPlainNumber(target) || target.equals(episode));
        String targetText = normalizeEpisodeText(target);
        String episodeText = normalizeEpisodeText(episode);
        return !isPlainNumber(target) && targetText.length() >= 2 && episodeText.contains(targetText);
    }

    private static boolean matchesTargetEpisode(AutoTarget target, String title) {
        if (target == null || TextUtils.isEmpty(target.episode)) return true;
        String episode = normalizeEpisode(title);
        if (!target.episodeTokens.isEmpty()) {
            if (isPlainNumber(target.episode) && !target.episode.equals(episode)) return false;
            return containsAllVariantTokens(title, target.episodeTokens);
        }
        return matchesEpisode(target.episode, title);
    }

    private static boolean isExtraEpisode(String title) {
        return EPISODE_EXTRA.matcher(Trans.t2s(false, title == null ? "" : title)).find();
    }

    private static boolean isUnexpectedExtraEpisode(AutoTarget target, String title) {
        return target != null && !isExtraEpisode(target.episodeName) && isExtraEpisode(title);
    }

    private static boolean matchesTitle(String targetTitle, String animeTitle, String season) {
        String target = normalizeTitleForSeason(targetTitle, season);
        String candidate = normalizeTitleForSeason(animeTitle, season);
        if (TextUtils.isEmpty(target) || TextUtils.isEmpty(candidate)) return false;
        if (isAlias(target, candidate)) return true;
        if (candidate.equals(target)) return true;
        if (candidate.startsWith(target)) {
            String suffix = candidate.substring(target.length());
            if (suffix.matches("^(第?[一二三四五六七八九十0-9]+季|s\\d+|season\\d+)$")) return true;
            if (!TextUtils.isEmpty(season) && suffix.length() <= 12 && suffix.endsWith("季")) return true;
        }
        if (target.startsWith(candidate) && candidate.matches("[a-z0-9]{4,}") && target.substring(candidate.length()).matches("[\\p{IsHan}]{2,16}")) return true;
        return false;
    }

    private static boolean matchesTargetTitle(AutoTarget target, String animeTitle) {
        return matchesTitle(target.title, animeTitle, target.season)
                || (!TextUtils.equals(target.title, target.searchTitle) && matchesTitle(target.searchTitle, animeTitle, target.season));
    }

    private static String normalizeTitleForSeason(String title, String season) {
        String value = removeRomanSeason(cleanMatchTitle(title), season);
        value = normalizeTitle(value);
        value = value.replaceAll("(?i)(?:第?[一二三四五六七八九十两俩零0-9]+季|s0*\\d+|season0*\\d+|part0*\\d+)$", "");
        if (!TextUtils.isEmpty(season)) {
            int seasonStart = value.length() - season.length();
            if (seasonStart > 0 && value.endsWith(season) && !Character.isDigit(value.charAt(seasonStart - 1))) value = value.substring(0, seasonStart);
        }
        return value;
    }

    private static boolean isNamedSeasonTitle(String targetTitle, String animeTitle, String season) {
        if (TextUtils.isEmpty(season)) return false;
        String target = normalizeTitleForSeason(targetTitle, season);
        String candidate = normalizeTitle(animeTitle);
        return !target.isEmpty() && candidate.startsWith(target) && candidate.substring(target.length()).matches("[\\p{L}\\p{N}]{2,12}季");
    }

    private static boolean isNamedSeasonTarget(AutoTarget target, String animeTitle) {
        return isNamedSeasonTitle(target.title, animeTitle, target.season)
                || (!TextUtils.equals(target.title, target.searchTitle) && isNamedSeasonTitle(target.searchTitle, animeTitle, target.season));
    }

    private static boolean isAlias(String first, String second) {
        first = first.replaceAll("(第?[一二三四五六七八九十0-9]+季|s\\d+|season\\d+)$", "");
        second = second.replaceAll("(第?[一二三四五六七八九十0-9]+季|s\\d+|season\\d+)$", "");
        if (first.equals(second)) return true;
        for (String alias : aliasesFor(first)) if (normalizeTitle(alias).equals(second)) return true;
        for (String alias : aliasesFor(second)) if (normalizeTitle(alias).equals(first)) return true;
        return false;
    }

    private static List<String> aliasesFor(String title) {
        List<String> result = new ArrayList<>();
        String normalized = normalizeTitle(title).replaceAll("(第?[一二三四五六七八九十0-9]+季|s\\d+|season\\d+)$", "");
        JSONObject rules = aliasRules();
        java.util.Iterator<String> keys = rules.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONArray values = rules.optJSONArray(key);
            if (values == null) continue;
            boolean matched = normalizeTitle(key).equals(normalized);
            for (int i = 0; i < values.length() && !matched; i++) matched = normalizeTitle(values.optString(i)).equals(normalized);
            if (!matched) continue;
            if (!normalizeTitle(key).equals(normalized)) result.add(key);
            for (int i = 0; i < values.length(); i++) if (!normalizeTitle(values.optString(i)).equals(normalized)) result.add(values.optString(i));
        }
        return result;
    }

    private static JSONObject aliasRules() {
        if (aliasRules != null) return aliasRules;
        synchronized (DanmakuApi.class) {
            if (aliasRules != null) return aliasRules;
            try (Scanner scanner = new Scanner(App.get().getAssets().open("danmaku/aliases.json"), StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                aliasRules = new JSONObject(scanner.hasNext() ? scanner.next() : "{}");
            } catch (Exception e) {
                aliasRules = new JSONObject();
            }
            return aliasRules;
        }
    }

    private static boolean matchesYear(String targetYear, String animeTitle) {
        if (TextUtils.isEmpty(targetYear)) return true;
        String candidateYear = extractYear(animeTitle);
        return TextUtils.isEmpty(candidateYear) || targetYear.equals(candidateYear);
    }

    private static boolean matchesType(String targetType, String candidateType) {
        if (TextUtils.isEmpty(targetType)) return true;
        String candidate = normalizeType(candidateType);
        return TextUtils.isEmpty(candidate) || targetType.equals(candidate);
    }

    private static String buildEpisodeSearchUrl(String baseUrl, String name, String episode) {
        String base = normalizeBaseUrl(baseUrl);
        return base + API_SEARCH_EPISODES + "?anime=" + Uri.encode(name) + "&episode=" + Uri.encode(episode);
    }

    private static String buildMatchFileName(AutoTarget target) {
        if (looksLikeMediaFileName(target.rawFileName)) {
            String fileTitle = extractFileSearchTitle(target.rawFileName);
            if (isUsableFileTitle(fileTitle) && !matchesTitle(target.title, fileTitle, target.season)) {
                String value = reconcileFileSeason(target.rawFileName.trim(), target.season);
                if (!TextUtils.isEmpty(target.platform) && !value.contains("@")) value += " @" + target.platform;
                return value;
            }
        }
        StringBuilder builder = new StringBuilder(target.title);
        if (!TextUtils.isEmpty(target.year)) builder.append(".").append(target.year);
        if (!TextUtils.isEmpty(target.episode)) {
            if (!TextUtils.isEmpty(target.season)) builder.append(".S").append(padEpisode(target.season)).append("E").append(padEpisode(target.episode));
            else builder.append(".第").append(target.episode).append("集");
        }
        if (!TextUtils.isEmpty(target.platform)) builder.append(" @").append(target.platform);
        return builder.toString();
    }

    private static String reconcileFileSeason(String fileName, String season) {
        if (TextUtils.isEmpty(season)) return fileName;
        Matcher matcher = FILE_SEASON_EPISODE.matcher(fileName);
        return matcher.find() ? matcher.replaceFirst("S" + padEpisode(season)) : fileName;
    }

    private static String buildCommentUrl(long episodeId) {
        return buildCommentUrl(normalizeBaseUrl(DanmakuSetting.getEffectiveApiUrl()), episodeId);
    }

    private static String buildCommentUrl(String baseUrl, long episodeId) {
        String base = normalizeBaseUrl(baseUrl);
        if (!TextUtils.isEmpty(BuildConfig.DANMAKU_PIZAZZ_BASE) && normalizeBaseUrl(BuildConfig.DANMAKU_PIZAZZ_BASE).equals(base)) return base + "/comment/" + episodeId + "?format=xml";
        return base + API_COMMENT + episodeId + "?format=xml";
    }

    private static String buildAiModelsUrl() {
        String base = DanmakuSetting.getAiBaseUrl();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/models";
    }

    private static String buildAiInput(AutoTarget target) throws Exception {
        JSONObject object = new JSONObject();
        object.put("name", target.title);
        object.put("file_name", target.rawFileName);
        object.put("ep", TextUtils.isEmpty(target.episode) ? JSONObject.NULL : parseInt(target.episode, 0));
        object.put("total", target.total);
        object.put("type", target.type);
        object.put("year", target.year);
        object.put("platform", target.platform);
        return object.toString();
    }

    private static String buildAiInputForLog(AutoTarget target) {
        try {
            return buildAiInput(target);
        } catch (Exception e) {
            return target.title;
        }
    }

    private static String normalizeBaseUrl(String url) {
        String base = url == null ? "" : url.trim();
        int apiIndex = base.indexOf("/api/");
        if (apiIndex >= 0) base = base.substring(0, apiIndex);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static String normalizeEpisode(String episode) {
        String value = cleanEpisodeLabel(episode);
        if (value.matches("^(正片|全片|正片播放|播放|全集)$")) return "";
        Matcher matcher = EPISODE_SEASON_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(1));
        matcher = EPISODE_MARKED_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(1));
        matcher = EPISODE_MARKED_CHINESE.matcher(value);
        if (matcher.find()) return String.valueOf(chineseNumber(matcher.group(1)));
        matcher = EPISODE_AFTER_SEASON_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(1));
        matcher = EPISODE_TRAILING_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(1));
        value = value.replaceAll("\\b(19|20)\\d{2}\\b", "");
        value = EPISODE_SEASON_TOKEN.matcher(value).replaceAll("");
        matcher = EPISODE_NUMBER.matcher(value);
        return matcher.find() ? parseEpisodeNumber(matcher.group(1)) : value;
    }

    private static String normalizeTargetEpisode(String episode, String type, int total) {
        String value = cleanEpisodeLabel(episode);
        Matcher matcher = EPISODE_SEASON_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(1));
        matcher = EPISODE_EXPLICIT_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(1));
        matcher = EPISODE_X_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(2));
        matcher = EPISODE_MARKED_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(1));
        matcher = EPISODE_MARKED_CHINESE.matcher(value);
        if (matcher.find()) return String.valueOf(chineseNumber(matcher.group(1)));
        matcher = EPISODE_AFTER_SEASON_NUMBER.matcher(value);
        if (matcher.find()) return parseEpisodeNumber(matcher.group(1));
        if ("movie".equals(type) || total <= 1) return "";
        matcher = EPISODE_LEADING_NUMBER.matcher(value);
        if (matcher.find()) {
            int number = parseInt(matcher.group(1), 0);
            if (number > 0 && number < 480) return String.valueOf(number);
        }
        if (value.matches("^0*\\d{1,4}(?:\\s*[集话話期])?$")) return parseEpisodeNumber(value.replaceAll("\\D", ""));
        String cleaned = value.replaceAll("(?i)\\.(mp4|mkv|avi|mov|flv|ts|m2ts|iso|wmv|webm)$", " ");
        cleaned = cleaned.replaceAll("(?<!\\d)(?:19|20)\\d{2}(?!\\d)", " ");
        cleaned = TARGET_FILE_NOISE.matcher(cleaned).replaceAll(" ");
        cleaned = FILE_NOISE.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("[._|｜/\\\\]+", " ").replaceAll("\\s+", " ").trim();
        matcher = EPISODE_TRAILING_NUMBER.matcher(cleaned);
        return matcher.find() ? parseEpisodeNumber(matcher.group(1)) : "";
    }

    private static boolean looksLikeMediaFileName(String value) {
        if (TextUtils.isEmpty(value)) return false;
        return value.matches("(?i).*(?:S\\d{1,2}E\\d{1,4}|\\.(?:mp4|mkv|avi|mov|flv|ts|m2ts|iso|wmv|webm)$|(?:19|20)\\d{2}|\\d{3,4}p|WEB[ ._-]?DL|Blu[ ._-]?Ray|x26[45]|H[ ._-]?26[45]).*");
    }

    private static String extractFileSearchTitle(String fileName) {
        if (!looksLikeMediaFileName(fileName)) return "";
        String value = sanitizeText(fileName);
        value = FILE_SIZE_SUFFIX.matcher(value).replaceFirst("");
        Matcher boundary = FILE_TITLE_EPISODE_BOUNDARY.matcher(value);
        if (boundary.find() && boundary.start() > 0) value = value.substring(0, boundary.start());
        value = cleanMatchTitle(value);
        value = value.replaceAll("(?<!\\d)(?:19|20)\\d{2}(?!\\d)", " ");
        value = TARGET_FILE_NOISE.matcher(value).replaceAll(" ");
        value = value.replaceAll("(?i)(?:^|\\s)(?:S\\d{1,2}|E\\d{1,4}|EP\\d{1,4})\\b.*$", " ");
        value = value.replaceAll("\\s+", " ").trim();
        return value.matches("\\d+") ? "" : value;
    }

    private static boolean isUsableFileTitle(String title) {
        if (TextUtils.isEmpty(title)) return false;
        String value = title.replaceAll("[【\\[（(][^】\\]）)]*[】\\]）)]", " ");
        value = value.replaceAll("[^\\p{IsHan}A-Za-z]+", "");
        int han = 0;
        int latin = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) han++;
            else if (Character.isLetter(c)) latin++;
        }
        return han >= 2 || latin >= 3;
    }

    private static boolean isPlaceholderTitle(String title) {
        String value = normalizeTitle(title);
        if (TextUtils.isEmpty(value)) return true;
        return value.matches("(?i)^(?:123|百度|夸克|uc|迅雷|阿里|天翼|网盘|云盘|盘搜|pansou|资源)+.*(?:资源|搜索结果)?$")
                || value.matches("(?i)^\\d*网盘资源$")
                || value.equals("搜索结果")
                || value.equals("网盘资源")
                || value.equals("云盘资源");
    }

    private static String cleanEpisodeLabel(String episode) {
        String value = sanitizeText(episode);
        value = value.replaceAll("【[^】]*】", " ");
        value = value.replaceAll("\\[[^\\]]*\\]", " ");
        value = value.replaceAll("(?i)^\\s*(qq|qiyi|iqiyi|youku|bilibili|mgtv|imgo|tencent)\\s*", " ");
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeEpisodeText(String text) {
        return cleanEpisodeLabel(text).replaceAll("[《》\\[\\]【】（）()\\s_\\-:：·.,，。/\\\\]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isPlainNumber(String value) {
        return value != null && value.matches("\\d+");
    }

    private static boolean containsAllVariantTokens(String title, List<String> required) {
        if (required == null || required.isEmpty()) return true;
        List<String> current = episodeVariantTokens(title);
        return current.containsAll(required);
    }

    private static List<String> episodeVariantTokens(String text) {
        String value = normalizeEpisodeText(text);
        List<String> tokens = new ArrayList<>();
        addSegmentToken(tokens, value, "上", "upper");
        addSegmentToken(tokens, value, "中", "middle");
        addSegmentToken(tokens, value, "下", "lower");
        addTokenIf(tokens, value.contains("粤语") || value.contains("广东话") || value.contains("粤配"), "cantonese");
        addTokenIf(tokens, value.contains("国语") || value.contains("普通话") || value.contains("国配") || value.contains("中配") || value.contains("中文配音"), "mandarin");
        addTokenIf(tokens, value.contains("原声") || value.contains("原版"), "original");
        addTokenIf(tokens, value.contains("日语") || value.contains("日版") || value.contains("日配"), "japanese");
        addTokenIf(tokens, value.contains("英语") || value.contains("英文") || value.contains("英配"), "english");
        addTokenIf(tokens, value.contains("韩语") || value.contains("韩版") || value.contains("韩配"), "korean");
        addTokenIf(tokens, value.contains("泰语") || value.contains("泰版") || value.contains("泰配"), "thai");
        return tokens;
    }

    private static void addSegmentToken(List<String> tokens, String value, String word, String token) {
        addTokenIf(tokens, value.equals(word) || value.contains(word + "集") || value.contains(word + "部") || value.contains(word + "篇"), token);
    }

    private static void addTokenIf(List<String> tokens, boolean condition, String token) {
        if (condition && !tokens.contains(token)) tokens.add(token);
    }

    private static String parseEpisodeNumber(String number) {
        try {
            return String.valueOf(Integer.parseInt(number));
        } catch (NumberFormatException e) {
            return number;
        }
    }

    private static String extractSeason(String text) {
        Matcher matcher = SEASON_NUMBER.matcher(sanitizeText(text));
        if (!matcher.find()) return "";
        String value = firstNonEmpty(firstNonEmpty(matcher.group(1), matcher.group(2)), matcher.group(3));
        int number = chineseNumber(value);
        return number > 0 ? String.valueOf(number) : "";
    }

    private static String extractRomanSeason(String text) {
        Matcher matcher = TITLE_ROMAN_SEASON_MARKER.matcher(sanitizeText(text));
        if (!matcher.find()) return "";
        int number = romanNumber(matcher.group(1));
        return number > 0 ? String.valueOf(number) : "";
    }

    private static String extractCandidateSeason(String text) {
        String season = extractSeason(text);
        if (!TextUtils.isEmpty(season)) return season;
        season = extractRomanSeason(text);
        if (!TextUtils.isEmpty(season)) return season;
        String value = cleanMatchTitle(text).replaceAll("[（(]\\s*(?:19|20)\\d{2}\\s*[）)].*$", "").trim();
        Matcher trailing = Pattern.compile("(?<!\\d)(\\d{1,2})$").matcher(value);
        if (!trailing.find()) return "";
        int number = parseInt(trailing.group(1), 0);
        return number > 0 && number <= 20 ? String.valueOf(number) : "";
    }

    private static int romanNumber(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "Ⅰ", "I" -> 1;
            case "Ⅱ", "II" -> 2;
            case "Ⅲ", "III" -> 3;
            case "Ⅳ", "IV" -> 4;
            case "Ⅴ", "V" -> 5;
            case "Ⅵ", "VI" -> 6;
            case "Ⅶ", "VII" -> 7;
            case "Ⅷ", "VIII" -> 8;
            case "Ⅸ", "IX" -> 9;
            case "Ⅹ", "X" -> 10;
            default -> 0;
        };
    }

    private static int chineseNumber(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        try { return Integer.parseInt(value); } catch (Exception ignored) {}
        value = value.replace('两', '二').replace('俩', '二').replace('〇', '零')
                .replace('壹', '一').replace('贰', '二').replace('叁', '三').replace('肆', '四').replace('伍', '五')
                .replace('陆', '六').replace('柒', '七').replace('捌', '八').replace('玖', '九').replace('拾', '十');
        String digits = "零一二三四五六七八九";
        if ("十".equals(value)) return 10;
        int ten = value.indexOf('十');
        if (ten >= 0) {
            int left = ten == 0 ? 1 : digits.indexOf(value.charAt(0));
            int right = ten == value.length() - 1 ? 0 : digits.indexOf(value.charAt(ten + 1));
            return Math.max(left, 0) * 10 + Math.max(right, 0);
        }
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = digits.indexOf(value.charAt(i));
            if (digit < 0) return 0;
            result = result * 10 + digit;
        }
        return result;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String normalizeTitle(String title) {
        String value = cleanMatchTitle(title);
        value = value.replaceAll("(?i)from\\s+\\S+$", "");
        value = value.replaceAll("【.*?】", "");
        value = value.replaceAll("\\(\\s*(?:19|20)\\d{2}\\s*\\)", "");
        value = value.replaceAll("（\\s*(?:19|20)\\d{2}\\s*）", "");
        value = Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[^\\p{L}\\p{N}]", "");
        return value.toLowerCase(Locale.ROOT);
    }

    private static String cleanAnimeSearchTitle(String title) {
        String value = cleanMatchTitle(title);
        value = value.replaceAll("(?i)\\s*from\\s+\\S+$", "");
        value = value.replaceAll("【.*?】", "");
        value = value.replaceAll("\\(\\s*(?:19|20)\\d{2}\\s*\\)", "");
        value = value.replaceAll("（\\s*(?:19|20)\\d{2}\\s*）", "");
        return value.trim();
    }

    private static String cleanMatchTitle(String title) {
        String value = sanitizeText(title);
        Matcher marker = TITLE_CONTENT_MARKER.matcher(value);
        if (marker.find()) value = value.substring(0, marker.start()).trim();
        value = selectSlashAlias(value);
        value = TITLE_YEAR_PREFIX.matcher(value).replaceFirst("");
        value = TITLE_CATEGORY_PREFIX.matcher(value).replaceFirst("");
        value = TITLE_DECORATION_PREFIX.matcher(value).replaceFirst("");
        value = TITLE_RELEASE_GROUP_PREFIX.matcher(value).replaceFirst("");
        value = TITLE_CAST_SUFFIX.matcher(value).replaceFirst("");
        value = TITLE_CAST_TEXT_SUFFIX.matcher(value).replaceFirst("");
        value = TITLE_EPISODE_RANGE.matcher(value).replaceAll(" ");
        value = TITLE_SEASON_COLLECTION.matcher(value).replaceAll(" ");
        value = TITLE_UPDATE_PROGRESS.matcher(value).replaceAll(" ");
        value = TITLE_COLLECTION_DETAIL_SUFFIX.matcher(value).replaceFirst(" ");
        value = TITLE_TECH_SUFFIX.matcher(value).replaceFirst(" ");
        value = value.replaceAll("(?i)\\.(mp4|mkv|avi|mov|flv|ts|m2ts|iso|wmv|webm)$", "");
        value = value.replace('《', ' ').replace('》', ' ').replace('〈', ' ').replace('〉', ' ');
        value = value.replaceAll("[._|｜/\\\\]+", " ");
        value = EPISODE_SEASON_NUMBER.matcher(value).replaceAll(" ");
        value = EPISODE_MARKED_NUMBER.matcher(value).replaceAll(" ");
        value = EPISODE_MARKED_CHINESE.matcher(value).replaceAll(" ");
        value = FILE_NOISE.matcher(value).replaceAll(" ");
        value = TITLE_BRACKET_NOISE.matcher(value).replaceAll(" ");
        value = value.replaceAll("(?i)\\b(?:资源|下载|网盘|夸克|百度网盘|迅雷云盘|在线观看)\\b", " ");
        value = value.replaceAll("\\s+-[A-Za-z0-9]{2,24}\\s*$", " ");
        value = TITLE_RELEASE_GROUP_PREFIX.matcher(value.trim()).replaceFirst("");
        String previous;
        do {
            previous = value;
            value = TITLE_SUBTITLE_SUFFIX.matcher(value).replaceFirst("").trim();
            value = TITLE_LANGUAGE_SUFFIX.matcher(value).replaceFirst("").trim();
            value = TITLE_BUNDLE_SUFFIX.matcher(value).replaceFirst("").trim();
            value = TITLE_COLLECTION_DETAIL_SUFFIX.matcher(value).replaceFirst("").trim();
            value = TITLE_RELEASE_SUFFIX.matcher(value).replaceFirst("").trim();
            value = TITLE_ATTACHED_NOISE.matcher(value).replaceFirst("").trim();
            value = TITLE_TRAILING_NOISE.matcher(value).replaceFirst("").trim();
        } while (!value.equals(previous));
        value = value.replaceAll("\\s+", " ").trim();
        return TITLE_EDGE_SEPARATOR.matcher(value).replaceAll("").trim();
    }

    private static String cleanTargetTitle(String title, String season) {
        String value = cleanMatchTitle(title);
        value = TITLE_METADATA_YEAR.matcher(value).replaceAll(" ");
        value = TITLE_SERIAL_SUFFIX.matcher(value).replaceFirst(" ");
        value = TITLE_SEASON_SUFFIX.matcher(value).replaceFirst(" ");
        value = removeRomanSeason(value, season);
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String sanitizeText(String text) {
        String value = Trans.t2s(false, text == null ? "" : text);
        value = INVISIBLE_TEXT.matcher(value).replaceAll("");
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
    }

    private static String removeRomanSeason(String title, String season) {
        if (TextUtils.isEmpty(season)) return title;
        Matcher matcher = TITLE_ROMAN_SEASON_MARKER.matcher(title);
        StringBuffer buffer = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            if (season.equals(String.valueOf(romanNumber(matcher.group(1))))) {
                matcher.appendReplacement(buffer, " ");
                changed = true;
                break;
            }
        }
        if (!changed) return title;
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String selectSlashAlias(String title) {
        int slash = title.indexOf('/');
        if (slash <= 0 || slash >= title.length() - 1) return title;
        String left = title.substring(0, slash).trim();
        String right = title.substring(slash + 1).trim();
        int metadata = right.indexOf('【');
        if (metadata >= 0) right = right.substring(0, metadata).trim();
        String leftKey = normalizeLooseKey(left);
        String rightKey = normalizeLooseKey(right);
        if (leftKey.isEmpty() || rightKey.isEmpty()) return title;
        if (leftKey.contains(rightKey) || rightKey.contains(leftKey)) return leftKey.length() >= rightKey.length() ? left : right;
        return title;
    }

    private static String normalizeLooseKey(String text) {
        return sanitizeText(text).replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }

    private static String normalizeType(String text) {
        String value = Trans.t2s(false, text == null ? "" : text).toLowerCase(Locale.ROOT);
        if (value.contains("movie") || value.contains("电影") || value.contains("剧场版")) return "movie";
        if (value.contains("variety") || value.contains("show") || value.contains("综艺")) return "variety";
        if (value.contains("anime") || value.contains("动漫") || value.contains("动画") || value.contains("番剧")) return "anime";
        if (value.equals("tv") || value.contains("drama") || value.contains("电视剧") || value.contains("国产剧") || value.contains("网剧") || value.contains("短剧") || value.contains("美剧") || value.contains("英剧") || value.contains("日剧") || value.contains("韩剧") || value.contains("泰剧")) return "tv";
        return "";
    }

    private static String extractYear(String text) {
        Matcher matcher = YEAR.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String padEpisode(String episode) {
        try {
            return String.format(Locale.US, "%02d", Integer.parseInt(episode));
        } catch (Exception e) {
            return episode;
        }
    }

    private static String buildName(String animeTitle, String episodeTitle) {
        if (TextUtils.isEmpty(animeTitle)) return episodeTitle;
        if (TextUtils.isEmpty(episodeTitle)) return animeTitle;
        return animeTitle + " " + episodeTitle;
    }

    private static String buildAnimeName(JSONObject anime) {
        StringBuilder builder = new StringBuilder(anime.optString("animeTitle"));
        String type = firstNonEmpty(anime.optString("typeDescription"), anime.optString("type"));
        String source = resolveSource(anime.optString("source"), anime.optString("animeTitle"), "", "");
        int count = anime.optInt("episodeCount", 0);
        if (!TextUtils.isEmpty(type)) builder.append(" · ").append(type);
        if (!TextUtils.isEmpty(source)) builder.append(" · ").append(source);
        if (count > 0) builder.append(" · ").append(count).append("集");
        return builder.toString();
    }

    private static String firstNonEmpty(String first, String second) {
        return TextUtils.isEmpty(first) ? (TextUtils.isEmpty(second) ? "" : second) : first;
    }

    private static boolean isSelectedAnime(JSONObject anime, Danmaku selectedAnime) {
        if (selectedAnime == null) return true;
        String animeId = anime.optString("animeId");
        if (!TextUtils.isEmpty(animeId) && (animeId.equals(selectedAnime.getAnimeId()) || animeId.equals(selectedAnime.getBangumiId()))) return true;
        String bangumiId = anime.optString("bangumiId");
        if (!TextUtils.isEmpty(bangumiId) && (bangumiId.equals(selectedAnime.getBangumiId()) || bangumiId.equals(selectedAnime.getAnimeId()))) return true;
        String title = anime.optString("animeTitle");
        return !TextUtils.isEmpty(title) && title.equals(selectedAnime.getAnimeTitle());
    }

    private static boolean isPreferredSource(AutoTarget target, String hint, String source, String title) {
        if (target != null && !target.platforms.isEmpty()) {
            for (String platform : target.platforms) if (isPreferredPlatform(platform, source, title)) return true;
            return false;
        }
        return isPreferredSource(hint, source, title);
    }

    private static boolean isPreferredSource(String hint, String source, String title) {
        String key = sourcePlatform(hint);
        if (key.isEmpty()) return false;
        return isPreferredPlatform(key, source, title);
    }

    private static boolean isPreferredPlatform(String key, String source, String title) {
        String expected = firstPlatform(normalizeSource(key));
        if (TextUtils.isEmpty(expected)) return false;
        if (hasPlatform(normalizeSource(source), expected)) return true;
        if (hasPlatform(sourceFromTitle(title), expected)) return true;
        if (hasPlatform(sourceFromEpisodeTitle(title), expected)) return true;
        String value = Trans.t2s(false, (source + " " + title).toLowerCase(Locale.ROOT));
        for (Map.Entry<String, String> entry : SOURCE_ALIASES.entrySet()) {
            if (expected.equals(entry.getValue()) && value.contains(entry.getKey())) return true;
        }
        return false;
    }

    private static String sourcePlatform(String hint) {
        List<String> platforms = sourcePlatforms(hint);
        return platforms.isEmpty() ? "" : platforms.get(0);
    }

    private static List<String> sourcePlatforms(String hint) {
        String direct = normalizeSource(hint);
        if (!TextUtils.isEmpty(direct)) return splitPlatforms(direct);
        String value = Trans.t2s(false, hint == null ? "" : hint).toLowerCase(Locale.ROOT);
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : SOURCE_ALIASES.entrySet()) {
            if (value.contains(entry.getKey())) addPlatform(items, entry.getValue());
        }
        return items;
    }

    private static String resolveSource(String source, String animeTitle, String episodeTitle, String fallback) {
        String value = normalizeSource(source);
        if (!TextUtils.isEmpty(value)) return value;
        value = sourceFromTitle(animeTitle);
        if (!TextUtils.isEmpty(value)) return value;
        value = sourceFromEpisodeTitle(episodeTitle);
        if (!TextUtils.isEmpty(value)) return value;
        return normalizeSource(fallback);
    }

    private static String sourceFromTitle(String title) {
        Matcher matcher = TITLE_SOURCE.matcher(title == null ? "" : title.trim());
        return matcher.find() ? normalizeSource(matcher.group(1)) : "";
    }

    private static String sourceFromEpisodeTitle(String title) {
        Matcher matcher = EPISODE_SOURCE_TAG.matcher(title == null ? "" : title);
        while (matcher.find()) {
            String source = normalizeSource(matcher.group(1));
            if (!TextUtils.isEmpty(source)) return source;
        }
        return "";
    }

    private static String normalizeSource(String source) {
        String value = Trans.t2s(false, source == null ? "" : source).trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(value)) return "";
        value = value.replace('＆', '&').replace('，', '&').replace(',', '&').replace('+', '&').replace('|', '&').replace('/', '&');
        List<String> items = new ArrayList<>();
        for (String part : value.split("&")) {
            String key = normalizeSourcePart(part);
            if (!TextUtils.isEmpty(key)) addPlatform(items, key);
        }
        return joinPlatforms(items);
    }

    private static String normalizeSourcePart(String part) {
        String value = part == null ? "" : part.trim();
        if (TextUtils.isEmpty(value)) return "";
        String alias = SOURCE_ALIASES.get(value);
        if (!TextUtils.isEmpty(alias)) return alias;
        value = value.replaceAll("[^a-z0-9_\\-]", "");
        alias = SOURCE_ALIASES.get(value);
        if (!TextUtils.isEmpty(alias)) return alias;
        return OFFICIAL_PLATFORMS.contains(value) ? value : "";
    }

    private static boolean hasPlatform(String platforms, String expected) {
        for (String platform : splitPlatforms(platforms)) if (platform.equals(expected)) return true;
        return false;
    }

    private static String firstPlatform(String platforms) {
        List<String> items = splitPlatforms(platforms);
        return items.isEmpty() ? "" : items.get(0);
    }

    private static List<String> splitPlatforms(String platforms) {
        List<String> items = new ArrayList<>();
        for (String part : (platforms == null ? "" : platforms).split("&")) addPlatform(items, normalizeSourcePart(part));
        return items;
    }

    private static void addPlatform(List<String> items, String platform) {
        if (!TextUtils.isEmpty(platform) && !items.contains(platform)) items.add(platform);
    }

    private static String joinPlatforms(List<String> items) {
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (builder.length() > 0) builder.append('&');
            builder.append(item);
        }
        return builder.toString();
    }

    public static void cancel() {
        OkHttp.cancel(TAG);
        OkHttp.cancel(TAG_AI);
    }

    private static String cachedBody(String key) {
        BodyCache cache = BODY_CACHE.get(key);
        if (cache == null) return null;
        if (System.currentTimeMillis() < cache.expire) return cache.body;
        BODY_CACHE.remove(key);
        return null;
    }

    private static void cacheBody(String key, String body, long ttl) {
        if (TextUtils.isEmpty(body)) return;
        BODY_CACHE.put(key, new BodyCache(body, ttl));
        if (BODY_CACHE.size() > 512) BODY_CACHE.clear();
    }

    private static boolean providerBlocked(String base) {
        Long until = PROVIDER_BLOCKED.get(base);
        return until != null && until > System.currentTimeMillis();
    }

    private static void providerSuccess(String base) {
        PROVIDER_FAILURES.remove(base);
        PROVIDER_BLOCKED.remove(base);
    }

    private static void providerFailure(String base) {
        int count = PROVIDER_FAILURES.merge(base, 1, Integer::sum);
        if (count >= 3) PROVIDER_BLOCKED.put(base, System.currentTimeMillis() + 60000L);
    }

    private static class AutoTarget {
        private final String title;
        private final String searchTitle;
        private final String season;
        private final String episode;
        private final String episodeName;
        private final String rawFileName;
        private final List<String> episodeTokens;
        private final List<String> titleTokens;
        private final String year;
        private final String type;
        private final String sourceHint;
        private final String platform;
        private final List<String> platforms;
        private final int total;

        private AutoTarget(String title, String searchTitle, String season, String episode, String episodeName, String rawFileName, List<String> episodeTokens, List<String> titleTokens, String year, String type, String sourceHint, String platform, List<String> platforms, int total) {
            this.title = title;
            this.searchTitle = searchTitle;
            this.season = season;
            this.episode = episode;
            this.episodeName = episodeName;
            this.rawFileName = rawFileName;
            this.episodeTokens = episodeTokens == null ? List.of() : episodeTokens;
            this.titleTokens = titleTokens == null ? List.of() : titleTokens;
            this.year = year;
            this.type = type;
            this.sourceHint = sourceHint;
            this.platform = platform;
            this.platforms = platforms == null ? List.of() : platforms;
            this.total = Math.max(total, 0);
        }

        private static AutoTarget create(String name, String episode, String sourceHint, Vod vod) {
            String rawTitle = TextUtils.isEmpty(name) && vod != null ? vod.getName() : name;
            String titleSource = rawTitle;
            int total = episodeTotal(vod);
            String sanitizedTitle = sanitizeText(rawTitle);
            String type = vod == null ? "" : normalizeType(vod.getTypeName() + " " + vod.getRemarks());
            boolean seasonCollection = SEASON_RANGE.matcher(sanitizedTitle).find() || TITLE_SEASON_COLLECTION.matcher(sanitizedTitle).find();
            String season = seasonCollection ? "" : extractSeason(sanitizedTitle);
            if (TextUtils.isEmpty(season) && (total > 1 || !"movie".equals(type))) season = extractRomanSeason(rawTitle);
            Matcher bareSeason = TITLE_BARE_SEASON.matcher(sanitizedTitle);
            if (TextUtils.isEmpty(season) && bareSeason.find()) {
                season = parseEpisodeNumber(bareSeason.group(1));
                titleSource = rawTitle.substring(0, Math.max(0, bareSeason.start())).trim();
            }
            String title = cleanTargetTitle(titleSource, season);
            String episodeName = cleanEpisodeLabel(episode);
            String fileTitle = extractFileSearchTitle(episode);
            if (isPlaceholderTitle(title) && !TextUtils.isEmpty(fileTitle)) title = fileTitle;
            if (TextUtils.isEmpty(season)) season = extractSeason(episodeName);
            String year = vod == null ? "" : extractYear(vod.getYear() + " " + vod.getRemarks());
            String number = normalizeTargetEpisode(episodeName, type, total);
            if (TextUtils.isEmpty(type) && TextUtils.isEmpty(number)) type = "movie";
            List<String> platforms = sourcePlatforms(sourceHint);
            String platform = platforms.isEmpty() ? "" : platforms.get(0);
            String searchTitle = cleanAnimeSearchTitle(rawTitle);
            return new AutoTarget(title, searchTitle, season, number, episodeName, episode == null ? "" : episode.trim(), episodeVariantTokens(episodeName), episodeVariantTokens(rawTitle), year, type, sourceHint, platform, platforms, total);
        }

        private static AutoTarget createForTest(String name, String episode, String sourceHint) {
            AutoTarget target = create(name, episode, sourceHint, null);
            String value = cleanEpisodeLabel(episode);
            if (!value.matches("0*\\d{1,4}")) return target;
            int number = parseInt(value, 0);
            if (number <= 0 || number >= 480) return target;
            String season = TextUtils.isEmpty(target.season) ? extractRomanSeason(name) : target.season;
            String title = cleanTargetTitle(name, season);
            return new AutoTarget(title, target.searchTitle, season, String.valueOf(number), target.episodeName, target.rawFileName, target.episodeTokens, target.titleTokens, target.year, "tv", target.sourceHint, target.platform, target.platforms, target.total);
        }

        private AutoTarget withTitle(String value) {
            return new AutoTarget(cleanTargetTitle(value, season), searchTitle, season, episode, episodeName, rawFileName, episodeTokens, titleTokens, year, type, sourceHint, platform, platforms, total);
        }

        private AutoTarget withAi(JSONObject value) {
            String aiTitle = value.optString("title").trim();
            String aiSeason = value.has("season") ? (value.isNull("season") ? "" : String.valueOf(value.optInt("season", 0))) : season;
            String aiEpisode = value.has("episode") ? (value.isNull("episode") ? "" : String.valueOf(value.optInt("episode", 0))) : episode;
            String aiYear = value.has("year") ? (value.isNull("year") ? "" : String.valueOf(value.optInt("year", 0))) : year;
            String aiType = value.optString("media_type", type);
            if ("0".equals(aiSeason)) aiSeason = "";
            if ("0".equals(aiEpisode)) aiEpisode = "";
            if ("0".equals(aiYear)) aiYear = "";
            return new AutoTarget(cleanTargetTitle(aiTitle, aiSeason), searchTitle, aiSeason, aiEpisode, episodeName, rawFileName, episodeTokens, titleTokens, aiYear, normalizeType(aiType), sourceHint, platform, platforms, total);
        }

        private static int episodeTotal(Vod vod) {
            if (vod == null) return 0;
            int total = 0;
            for (var flag : vod.getFlags()) total = Math.max(total, flag.getEpisodes().size());
            return total;
        }
    }

    public static class AiMatchTestResult {
        private final String input;
        private final String fileName;
        private final String rawBody;
        private final List<Danmaku> matches;
        private final long elapsedMs;

        private AiMatchTestResult(String input, String fileName, String rawBody, List<Danmaku> matches, long elapsedMs) {
            this.input = input;
            this.fileName = fileName;
            this.rawBody = rawBody;
            this.matches = matches;
            this.elapsedMs = elapsedMs;
        }

        public String getInput() {
            return input;
        }

        public String getFileName() {
            return fileName;
        }

        public String getRawBody() {
            return rawBody;
        }

        public List<Danmaku> getMatches() {
            return matches;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }
    }

    private static final class AiCleanResult {
        private final AutoTarget target;
        private final String input;
        private final String rawResponse;

        private AiCleanResult(AutoTarget target, String input, String rawResponse) {
            this.target = target;
            this.input = input;
            this.rawResponse = rawResponse;
        }
    }

    private static final class AutoCache {
        final Danmaku item;
        final long time = System.currentTimeMillis();
        AutoCache(Danmaku item) { this.item = item; }
    }

    private static final class BodyCache {
        final String body;
        final long expire;
        BodyCache(String body, long ttl) { this.body = body; this.expire = System.currentTimeMillis() + ttl; }
    }
}
