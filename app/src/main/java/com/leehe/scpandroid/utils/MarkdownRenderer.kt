package com.leehe.scpandroid.utils

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownRenderer {

    private val options = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            TaskListExtension.create(),
            StrikethroughExtension.create()
        ))
        set(TablesExtension.CLASS_NAME, "md-table")
        set(TablesExtension.COLUMN_SPANS, false)
    }

    private val parser: Parser by lazy { Parser.builder(options).build() }
    private val renderer: HtmlRenderer by lazy { HtmlRenderer.builder(options).build() }

    fun toHtml(markdown: String, isDark: Boolean): String {
        var text = EmojiMap.replaceShortcodes(markdown)
        // 上标 ^text^ 和下标 ~text~ (先保护 ~~删除线~~，再替换单符号)
        text = text.replace("~~", "\u0001DEL\u0001")  // 临时保护删除线
        text = text.replace(Regex("~([^~]+)~"), "<sub>$1</sub>")
        text = text.replace("\u0001DEL\u0001", "~~")  // 还原删除线
        text = text.replace(Regex("\\^([^\\^]+)\\^"), "<sup>$1</sup>")
        // 高亮 ==text==
        text = text.replace(Regex("==([^=]+)=="), "<mark>$1</mark>")
        val body = renderer.render(parser.parse(text))
        val theme = if (isDark) darkCss else lightCss
        return "<!DOCTYPE html><html><head>" +
               "<meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=3'>" +
               "<style>$commonCss $theme</style>" +
               "</head><body>$body</body></html>"
    }

    // ==================== CSS ====================

    private val commonCss = ("" +
    "*{box-sizing:border-box;margin:0;padding:0}" +
    "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;" +
    "font-size:16px;line-height:1.7;padding:4px 16px 48px 16px;" +
    "overflow-wrap:break-word;word-break:break-word}" +
    "h1,h2,h3,h4,h5,h6{font-weight:600;margin-top:1.2em;margin-bottom:0.4em;line-height:1.3}" +
    "h1{font-size:1.75em;padding-bottom:0.3em;border-bottom:2px solid var(--bd)}" +
    "h2{font-size:1.45em;padding-bottom:0.25em;border-bottom:1.5px solid var(--bd)}" +
    "h3{font-size:1.2em}" +
    "h4{font-size:1.1em}" +
    "h5{font-size:1.0em}" +
    "h6{font-size:0.9em}" +
    "p{margin:0.4em 0;line-height:1.7}" +
    "strong{font-weight:600}" +
    "em{font-style:italic}" +
    ":not(pre)>code{font-family:'JetBrains Mono','Fira Code',monospace;font-size:0.88em;" +
    "background:var(--icbg);color:var(--ic);padding:1.5px 5px;border-radius:4px}" +
    "pre{margin:0.6em 0;padding:10px 14px;border-radius:6px;" +
    "background:var(--cbg);color:var(--cfg);font-family:'JetBrains Mono','Fira Code',monospace;" +
    "font-size:0.85em;line-height:1.55;overflow-x:auto;" +
    "-webkit-overflow-scrolling:touch;white-space:pre-wrap;word-wrap:break-word}" +
    "pre code{background:none!important;color:inherit;font-size:inherit;padding:0;border-radius:0}" +
    "blockquote{margin:0.6em 0;padding:6px 14px;border-left:3.5px solid var(--bl);" +
    "color:var(--bt);background:var(--bbg);border-radius:0 4px 4px 0}" +
    "blockquote p{margin:0.2em 0}" +
    "hr{margin:1.2em 0;border:none;border-top:2px solid var(--bd)}" +
    "ul,ol{margin:0.4em 0;padding-left:1.8em}" +
    "li{margin:0.15em 0}" +
    "li>ul,li>ol{margin:0.1em 0}" +
    "ul{list-style-type:disc}" +
    "ul ul{list-style-type:circle}" +
    "ul ul ul{list-style-type:square}" +
    "ol{list-style-type:decimal}" +
    ".md-task-list-item{list-style-type:none!important;margin-left:-1.5em}" +
    ".md-task-list-item input[type=checkbox]{margin:0 6px 0 0;vertical-align:middle;accent-color:var(--ac)}" +
    ".md-table{margin:0.6em 0;border-collapse:collapse;width:100%;display:block;overflow-x:auto}" +
    ".md-table th,.md-table td{border:1px solid var(--bd);padding:6px 10px;text-align:left}" +
    ".md-table th{background:var(--hbg);font-weight:600;white-space:nowrap}" +
    ".md-table tr:nth-child(even){background:var(--rb)}" +
    ".md-table thead{border-bottom:2px solid var(--bd)}" +
    "del,s{text-decoration:line-through;color:var(--dl)}" +
    "sup,sub{font-size:0.8em}" +
    "sup{vertical-align:super}" +
    "sub{vertical-align:sub}" +
    "a{color:var(--al);text-decoration:none}" +
    "a:hover{text-decoration:underline}" +
    "img{max-width:100%;height:auto;border-radius:4px;margin:0.4em 0}" +
    "mark{background:#ff0;color:inherit;padding:0 2px;border-radius:2px}")

    private val lightCss = ("" +
    ":root{--bd:#d0d7de;--ac:#d65b1b;--bl:#dfe2e5;--bt:#57606a;--bbg:#f6f8fa;" +
    "--icbg:#afb8c133;--ic:#c41a16;--cbg:#f6f8fa;--cfg:#1f2328;" +
    "--hbg:#f6f8fa;--rb:#f6f8fa55;--dl:#6e7681;--al:#0969da}" +
    "body{color:#1f2328;background:#ffffff}")

    private val darkCss = ("" +
    ":root{--bd:#30363d;--ac:#f78166;--bl:#30363d;--bt:#8b949e;--bbg:#161b22;" +
    "--icbg:#6e768133;--ic:#ff7b72;--cbg:#161b22;--cfg:#c9d1d9;" +
    "--hbg:#161b22;--rb:#161b2255;--dl:#8b949e;--al:#58a6ff}" +
    "body{color:#c9d1d9;background:#0d1117}")

    // ==================== Emoji ====================

    private object EmojiMap {
        private val map = mapOf(
            "smile" to "\uD83D\uDE04", "laughing" to "\uD83D\uDE06", "joy" to "\uD83D\uDE02",
            "rofl" to "\uD83E\uDD23", "smiley" to "\uD83D\uDE03", "grin" to "\uD83D\uDE01",
            "wink" to "\uD83D\uDE09", "blush" to "\uD83D\uDE0A", "yum" to "\uD83D\uDE0B",
            "heart_eyes" to "\uD83D\uDE0D", "kissing_heart" to "\uD83D\uDE18",
            "stuck_out_tongue" to "\uD83D\uDE1D", "sweat_smile" to "\uD83D\uDE05",
            "thinking" to "\uD83E\uDD14", "neutral_face" to "\uD83D\uDE10",
            "expressionless" to "\uD83D\uDE11", "unamused" to "\uD83D\uDE12",
            "sweat" to "\uD83D\uDE13", "pensive" to "\uD83D\uDE14", "confused" to "\uD83D\uDE15",
            "disappointed" to "\uD83D\uDE1E", "worried" to "\uD83D\uDE1F",
            "cry" to "\uD83D\uDE22", "sob" to "\uD83D\uDE2D", "angry" to "\uD83D\uDE20",
            "rage" to "\uD83D\uDE21", "triumph" to "\uD83D\uDE24",
            "sleepy" to "\uD83D\uDE2A", "tired_face" to "\uD83D\uDE2B",
            "mask" to "\uD83D\uDE37", "sunglasses" to "\uD83D\uDE0E",
            "dizzy_face" to "\uD83D\uDE35", "astonished" to "\uD83D\uDE32",
            "zipper_mouth" to "\uD83E\uDD10", "nerd" to "\uD83E\uDD13",
            "partying" to "\uD83E\uDD73", "star_struck" to "\uD83E\uDD29",
            "pleading" to "\uD83E\uDD7A", "shushing" to "\uD83E\uDD2B",
            "yawning" to "\uD83E\uDD71", "hot" to "\uD83E\uDD75", "cold" to "\uD83E\uDD76",
            "clown" to "\uD83E\uDD21", "skull" to "\uD83D\uDC80", "poop" to "\uD83D\uDCA9",
            "alien" to "\uD83D\uDC7D", "robot" to "\uD83E\uDD16",
            "wave" to "\uD83D\uDC4B", "hand" to "\u270B", "ok_hand" to "\uD83D\uDC4C",
            "point_up" to "\u261D", "point_down" to "\uD83D\uDC47",
            "point_left" to "\uD83D\uDC48", "point_right" to "\uD83D\uDC49",
            "pray" to "\uD83D\uDE4F", "clap" to "\uD83D\uDC4F", "muscle" to "\uD83D\uDCAA",
            "thumbsup" to "\uD83D\uDC4D", "thumbsdown" to "\uD83D\uDC4E",
            "fist" to "\u270A", "v" to "\u270C", "metal" to "\uD83E\uDD18",
            "tada" to "\uD83C\uDF89",
            "heart" to "\u2764", "broken_heart" to "\uD83D\uDC94",
            "star" to "\u2B50", "star2" to "\uD83C\uDF1F", "fire" to "\uD83D\uDD25",
            "zap" to "\u26A1", "boom" to "\uD83D\uDCA5", "sparkles" to "\u2728",
            "100" to "\uD83D\uDCAF", "question" to "\u2753", "bulb" to "\uD83D\uDCA1",
            "lock" to "\uD83D\uDD12", "key" to "\uD83D\uDD11",
            "rocket" to "\uD83D\uDE80", "airplane" to "\u2708", "car" to "\uD83D\uDE97",
            "bike" to "\uD83D\uDEB2", "gift" to "\uD83C\uDF81", "package" to "\uD83D\uDCE6",
            "book" to "\uD83D\uDCD5", "books" to "\uD83D\uDCDA", "pencil" to "\u270F",
            "clipboard" to "\uD83D\uDCCB", "paperclip" to "\uD83D\uDCCE",
            "mag" to "\uD83D\uDD0D", "pushpin" to "\uD83D\uDCCC",
            "sunny" to "\u2600", "cloud" to "\u2601", "snowflake" to "\u2744",
            "ocean" to "\uD83C\uDF0A", "rainbow" to "\uD83C\uDF08",
            "coffee" to "\u2615", "tea" to "\uD83C\uDF75", "beer" to "\uD83C\uDF7A",
            "pizza" to "\uD83C\uDF55", "cake" to "\uD83C\uDF70",
            "apple" to "\uD83C\uDF4E", "banana" to "\uD83C\uDF4C",
            "check" to "\u2705", "x" to "\u274C", "warning" to "\u26A0\uFE0F",
            "info" to "\u2139\uFE0F", "bug" to "\uD83D\uDC1B", "link" to "\uD83D\uDD17",
            "arrows_clockwise" to "\uD83D\uDD04", "hourglass" to "\u231B",
            "timer" to "\u23F2", "stopwatch" to "\u23F1", "clock" to "\uD83D\uDD53",
            "bell" to "\uD83D\uDD14"
        )
        private val pattern = Regex(":([a-z0-9_+-]+):")
        fun replaceShortcodes(text: String): String {
            return pattern.replace(text) { mr -> map[mr.groupValues[1]] ?: mr.value }
        }
    }
}
