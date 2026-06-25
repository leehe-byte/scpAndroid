package com.leehe.scpandroid.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * 基于 Notepad4 配色方案的综合语法高亮器
 */
object CodeHighlighter {

    // ==================== Notepad4 Color Palette ====================
    private val cKeyword      = Color(0xFF00B050)  // Green (通用关键词)
    private val cKeywordBold  = Color(0xFFFF8000)  // Orange (python/sh/batch等加粗关键词)
    private val cType         = Color(0xFF007F7F)  // Teal (类型)
    private val cTypeAlt      = Color(0xFF0080FF)  // Blue (类型备选)
    private val cClass        = Color(0xFF0080FF)  // Blue (类名)
    private val cInterface    = Color(0xFF1E90FF)  // Dodger Blue (接口)
    private val cFunction     = Color(0xFFA46000)  // Brown-orange (函数)
    private val cFuncDef      = Color(0xFF0080C0)  // Cyan-blue bold (函数定义)
    private val cString       = Color(0xFF008000)  // Green (字符串)
    private val cStringRaw    = Color(0xFFF08000)  // Dark Orange (raw/triple 字符串)
    private val cStringAlt    = Color(0xFF008080)  // Teal-green (替代字符串)
    private val cComment      = Color(0xFF608060)  // Gray-green (注释)
    private val cDocComment   = Color(0xFF408040)  // Darker green (文档注释)
    private val cDocTag       = Color(0xFF408080)  // Teal-gray (文档注释标签)
    private val cNumber       = Color(0xFFF84C4C)  // Red (数字)
    private val cOperator     = Color(0xFFA349A4)  // Purple (操作符)
    private val cConstant     = Color(0xFFA349A4)  // Purple (常量)
    private val cAnnotation   = Color(0xFFFF8000)  // Orange (注解/装饰器/属性)
    private val cEscape       = Color(0xFF0080C0)  // Light blue (转义序列)
    private val cPreprocessor = Color(0xFFFF8000)  // Orange (预处理器指令)
    private val cProperty     = Color(0xFF648000)  // Olive (属性/键)
    private val cRegex        = Color(0xFF006633)  // Dark green (正则)
    private val cRegexBg      = Color(0xFF0F405D)  // Dark blue bg (正则背景)
    private val cVariable     = Color(0xFF9E4D2A)  // Brown (变量)
    private val cLabel        = Color(0xFF404030)  // Dark background (标签)
    private val cMarkup       = Color(0xFF881280)  // Purple (XML 标识)
    private val cHtmlTag      = Color(0xFF648000)  // Olive (HTML 标签)
    private val cHtmlAttr     = Color(0xFFFF4000)  // Red-orange (HTML 属性)
    private val cEntity       = Color(0xFFA46000)  // Brown (HTML 实体)
    private val cCssProperty  = Color(0xFFFF4000)  // Red-orange (CSS 属性)
    private val cCssValue     = Color(0xFF3A6EA5)  // Steel blue (CSS 值)
    private val cDirective    = Color(0xFFFF8000)  // Orange (指令)
    private val cInstruction  = Color(0xFF0080FF)  // Blue (指令)
    private val cRegister     = Color(0xFFFF8000)  // Orange (寄存器)
    private val cBacktick     = Color(0xFF8E0D90)  // Purple (反引号)
    private val cDecorator    = Color(0xFFC65D09)  // Dark orange (装饰器)
    private val cBuiltin      = Color(0xFF0080C0)  // Cyan (内置函数/常量)
    private val cHeading      = Color(0xFF0080FF)  // Blue (Markdown 标题)
    private val cBold         = Color.Unspecified  // 无固定色（bold 样式用）
    private val cLink         = Color(0xFF3A6EA5)  // Steel blue (链接)
    private val cInserted     = Color(0xFF085820)  // Green background (diff 新增)
    private val cRemoved      = Color(0xFFA52A2A)  // Red background (diff 删除)

    // ==================== Extension → Language Mapping ====================
    // 来源: Notepad4 [File Extensions]

    private fun langFor(ext: String): String = when (ext) {
        "c", "cpp", "cxx", "cc", "h", "hpp", "hxx", "hh", "inl", "cuh" -> "cpp"
        "cs", "csx" -> "cs"
        "css", "scss", "less", "wxss" -> "css"
        "java", "aidl" -> "java"
        "js", "jsx", "cjs", "mjs" -> "js"
        "json", "jsonc", "json5" -> "json"
        "py", "pyw", "pyx", "pyi", "py3" -> "py"
        "rb", "rbw", "gemspec" -> "rb"
        "sql", "mysql" -> "sql"
        "html", "htm", "shtml", "xhtml", "vue", "hbs", "svelte" -> "html"
        "xml", "xsl", "xslt", "xsd", "rss", "svg", "xaml", "plist", "pom", "wxml" -> "xml"
        "smali" -> "smali"
        "asm", "s", "a51" -> "asm"
        "ahk" -> "ahk"
        "bat", "cmd" -> "bat"
        "cmake" -> "cmake"
        "conf", "cfg", "cnf", "htaccess", "properties", "prefs" -> "conf"
        "csv", "tsv" -> "csv"
        "d", "di" -> "d"
        "dart" -> "dart"
        "diff", "patch" -> "diff"
        "f", "for", "f90", "f95" -> "f"
        "go" -> "go"
        "gradle" -> "gradle"
        "groovy", "gvy", "gy", "gsh" -> "groovy"
        "hs" -> "hs"
        "hx" -> "hx"
        "ini", "inf", "reg", "url" -> "ini"
        "jl" -> "jl"
        "kt", "kts" -> "kt"
        "tex", "sty", "cls" -> "tex"
        "lisp", "el", "lsp", "clj", "cljs", "scm" -> "lisp"
        "lua", "nse" -> "lua"
        "mak", "make", "mk", "dsp" -> "make"
        "md", "markdown", "mdown", "mkdn", "rmd" -> "md"
        "ml", "mli" -> "ml"
        "nim", "nims" -> "nim"
        "nsi", "nsh" -> "nsi"
        "pas", "dpr", "pp" -> "pas"
        "php", "phps", "phpt", "phtml" -> "php"
        "pl", "pm", "cgi" -> "pl"
        "ps1", "psc1", "psd1", "psm1" -> "ps1"
        "r" -> "r"
        "rc", "rc2" -> "rc"
        "rs" -> "rs"
        "sas" -> "sas"
        "scala", "sbt" -> "scala"
        "sh", "zsh", "bash", "ac" -> "sh"
        "swift" -> "swift"
        "tcl" -> "tcl"
        "toml" -> "toml"
        "ts", "tsx", "cts", "mts" -> "ts"
        "vbs" -> "vbs"
        "v", "vh", "sv" -> "v"
        "vhd", "vhdl" -> "vhdl"
        "vim" -> "vim"
        "vb", "bas", "frm", "cls" -> "vb"
        "wat", "wast" -> "wat"
        "yaml", "yml", "clang-format", "clang-tidy" -> "yaml"
        "zig" -> "zig"
        "log" -> "log"
        "txt", "text" -> "txt"
        else -> ""
    }

    // ==================== Keywords / Types per Language ====================

    private fun keywords(lang: String): Set<String> = when (lang) {
        "py" -> setOf("and", "as", "assert", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "finally", "for", "from", "global", "if", "import",
            "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield", "async", "await")
        "java" -> setOf("abstract", "assert", "break", "case", "catch", "class", "continue",
            "default", "do", "else", "enum", "extends", "final", "finally", "for", "goto",
            "if", "implements", "import", "instanceof", "interface", "native", "new",
            "package", "private", "protected", "public", "return", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "volatile", "while", "var", "record", "sealed", "permits")
        "kt" -> setOf("abstract", "actual", "annotation", "as", "break", "class", "companion",
            "const", "constructor", "continue", "crossinline", "data", "do", "else",
            "enum", "expect", "external", "final", "finally", "for", "fun", "if", "import",
            "in", "infix", "init", "inline", "inner", "interface", "internal", "is",
            "lateinit", "noinline", "object", "open", "operator", "out", "override",
            "package", "private", "protected", "public", "reified", "return", "sealed",
            "suspend", "super", "tailrec", "this", "throw", "try", "typealias", "val",
            "var", "vararg", "when", "while")
        "cpp" -> setOf("alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand",
            "bitor", "bool", "break", "case", "catch", "char", "char8_t", "char16_t",
            "char32_t", "class", "compl", "concept", "const", "consteval", "constexpr",
            "constinit", "const_cast", "continue", "co_await", "co_return", "co_yield",
            "decltype", "default", "delete", "do", "double", "dynamic_cast", "else",
            "enum", "explicit", "export", "extern", "false", "float", "for", "friend",
            "goto", "if", "inline", "int", "long", "mutable", "namespace", "new",
            "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq",
            "private", "protected", "public", "register", "reinterpret_cast", "return",
            "short", "signed", "sizeof", "static", "static_assert", "static_cast",
            "struct", "switch", "template", "this", "thread_local", "throw", "true",
            "try", "typedef", "typeid", "typename", "union", "unsigned", "using",
            "virtual", "void", "volatile", "wchar_t", "while", "xor", "xor_eq")
        "cs" -> setOf("abstract", "as", "base", "bool", "break", "byte", "case", "catch",
            "char", "checked", "class", "const", "continue", "decimal", "default",
            "delegate", "do", "double", "else", "enum", "event", "explicit", "extern",
            "false", "finally", "fixed", "float", "for", "foreach", "goto", "if",
            "implicit", "in", "int", "interface", "internal", "is", "lock", "long",
            "namespace", "new", "null", "object", "operator", "out", "override", "params",
            "private", "protected", "public", "readonly", "record", "ref", "return",
            "sbyte", "sealed", "short", "sizeof", "stackalloc", "static", "string",
            "struct", "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong",
            "unchecked", "unsafe", "ushort", "using", "var", "virtual", "void", "volatile",
            "while", "yield")
        "js" -> setOf("async", "await", "break", "case", "catch", "class", "const",
            "continue", "debugger", "default", "delete", "do", "else", "enum", "export",
            "extends", "false", "finally", "for", "function", "if", "import", "in",
            "instanceof", "let", "new", "null", "of", "return", "super", "switch",
            "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while",
            "with", "yield")
        "ts" -> setOf("abstract", "as", "asserts", "async", "await", "break", "case",
            "catch", "class", "const", "continue", "debugger", "declare", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally",
            "for", "from", "function", "if", "implements", "import", "in", "infer",
            "instanceof", "interface", "is", "keyof", "let", "module", "namespace",
            "never", "new", "null", "of", "override", "private", "protected", "public",
            "readonly", "return", "satisfies", "static", "super", "switch", "symbol",
            "this", "throw", "true", "try", "type", "typeof", "undefined", "unique",
            "var", "void", "while", "with", "yield")
        "go" -> setOf("break", "case", "chan", "const", "continue", "default", "defer",
            "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
            "interface", "map", "package", "range", "return", "select", "struct",
            "switch", "type", "var")
        "rs" -> setOf("as", "async", "await", "break", "const", "continue", "crate",
            "dyn", "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
            "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
            "self", "Self", "static", "struct", "super", "trait", "true", "type",
            "union", "unsafe", "use", "where", "while")
        "swift" -> setOf("as", "associatedtype", "break", "case", "catch", "class",
            "continue", "default", "defer", "deinit", "do", "else", "enum", "extension",
            "fallthrough", "false", "fileprivate", "for", "func", "guard", "if", "import",
            "in", "init", "inout", "internal", "is", "let", "nil", "open", "operator",
            "precedencegroup", "private", "protocol", "public", "repeat", "return",
            "self", "Self", "static", "struct", "subscript", "super", "switch", "throw",
            "throws", "true", "try", "typealias", "var", "where", "while")
        "dart" -> setOf("abstract", "as", "assert", "async", "await", "break", "case",
            "catch", "class", "const", "continue", "covariant", "default", "deferred",
            "do", "dynamic", "else", "enum", "export", "extends", "extension", "external",
            "factory", "false", "final", "finally", "for", "Function", "get", "hide",
            "if", "implements", "import", "in", "interface", "is", "late", "library",
            "mixin", "new", "null", "on", "operator", "part", "required", "rethrow",
            "return", "sealed", "set", "show", "static", "super", "switch", "sync",
            "this", "throw", "true", "try", "typedef", "var", "void", "while", "with",
            "yield")
        "groovy" -> setOf("abstract", "as", "assert", "break", "case", "catch", "class",
            "const", "continue", "def", "default", "do", "else", "enum", "extends",
            "false", "final", "finally", "for", "if", "implements", "import", "in",
            "instanceof", "interface", "native", "new", "null", "package", "private",
            "protected", "public", "return", "static", "super", "switch", "synchronized",
            "this", "throw", "throws", "trait", "transient", "true", "try", "var",
            "void", "volatile", "while")
        "scala" -> setOf("abstract", "case", "catch", "class", "def", "do", "else",
            "extends", "false", "final", "finally", "for", "forSome", "given", "if",
            "implicit", "import", "lazy", "match", "new", "null", "object", "override",
            "package", "private", "protected", "return", "sealed", "super", "then",
            "this", "throw", "trait", "true", "try", "type", "using", "val", "var",
            "while", "with", "yield")
        "rb" -> setOf("alias", "and", "begin", "break", "case", "class", "def", "defined?",
            "do", "else", "elsif", "end", "ensure", "false", "for", "if", "in",
            "module", "next", "nil", "not", "or", "redo", "rescue", "retry", "return",
            "self", "super", "then", "true", "undef", "unless", "until", "when", "while",
            "yield")
        "php" -> setOf("abstract", "and", "array", "as", "break", "callable", "case",
            "catch", "class", "clone", "const", "continue", "declare", "default", "die",
            "do", "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach",
            "endif", "endswitch", "endwhile", "eval", "exit", "extends", "final",
            "finally", "fn", "for", "foreach", "function", "global", "goto", "if",
            "implements", "include", "instanceof", "insteadof", "interface", "isset",
            "list", "match", "namespace", "new", "or", "print", "private", "protected",
            "public", "readonly", "require", "return", "static", "switch", "throw",
            "trait", "try", "unset", "use", "var", "while", "xor", "yield")
        "lua" -> setOf("and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat",
            "return", "then", "true", "until", "while")
        "r" -> setOf("break", "else", "FALSE", "for", "function", "if", "in", "Inf",
            "NA", "NA_character_", "NA_complex_", "NA_integer_", "NA_real_", "NaN",
            "next", "NULL", "repeat", "TRUE", "while")
        "ps1" -> setOf("begin", "break", "catch", "class", "continue", "data", "do",
            "dynamicparam", "else", "elseif", "end", "enum", "exit", "filter", "finally",
            "for", "foreach", "from", "function", "hidden", "if", "in", "param",
            "process", "return", "static", "switch", "throw", "trap", "try", "until",
            "using", "var", "while")
        "sql" -> setOf("ADD", "ALL", "ALTER", "AND", "AS", "ASC", "BETWEEN", "BY",
            "CASE", "CREATE", "DATABASE", "DEFAULT", "DELETE", "DESC", "DISTINCT",
            "DROP", "ELSE", "EXISTS", "FROM", "GRANT", "GROUP", "HAVING", "IN", "INDEX",
            "INSERT", "INTO", "IS", "JOIN", "KEY", "LEFT", "LIKE", "LIMIT", "NOT",
            "NULL", "ON", "OR", "ORDER", "OUTER", "PRIMARY", "REFERENCES", "RIGHT",
            "SELECT", "SET", "TABLE", "THEN", "TO", "UNION", "UNIQUE", "UPDATE",
            "VALUES", "VIEW", "WHEN", "WHERE")
        "sh" -> setOf("case", "do", "done", "elif", "else", "esac", "fi", "for",
            "function", "if", "in", "select", "then", "until", "while")
        "bat" -> setOf("call", "echo", "else", "exit", "for", "goto", "if", "pause",
            "rem", "set", "shift", "start")
        "perl" -> setOf("if", "else", "elsif", "unless", "while", "until", "for",
            "foreach", "next", "last", "redo", "return", "sub", "my", "our", "local",
            "use", "require", "package", "do", "eval", "exit", "die", "warn", "print",
            "say", "open", "close", "if", "given", "when", "default")
        "nim" -> setOf("addr", "and", "as", "asm", "bind", "block", "break", "case",
            "cast", "concept", "const", "continue", "converter", "defer", "discard",
            "distinct", "div", "do", "elif", "else", "end", "enum", "except", "export",
            "finally", "for", "from", "func", "if", "import", "in", "include", "is",
            "isnot", "iterator", "let", "macro", "method", "mixin", "mod", "nil",
            "not", "notin", "object", "of", "or", "out", "proc", "ptr", "raise",
            "ref", "return", "shl", "shr", "static", "template", "try", "tuple",
            "type", "using", "var", "when", "while", "xor", "yield")
        "zig" -> setOf("addrspace", "align", "and", "anyframe", "anytype", "asm", "async",
            "await", "break", "catch", "comptime", "const", "continue", "defer", "else",
            "enum", "errdefer", "error", "export", "extern", "false", "fn", "for",
            "if", "inline", "noalias", "noinline", "null", "or", "orelse", "packed",
            "pub", "resume", "return", "struct", "suspend", "switch", "test", "threadlocal",
            "true", "try", "union", "unreachable", "usingnamespace", "var", "volatile",
            "while")
        "jl" -> setOf("abstract", "begin", "break", "catch", "const", "continue", "do",
            "else", "elseif", "end", "export", "false", "finally", "for", "function",
            "global", "if", "import", "in", "isa", "let", "local", "macro", "module",
            "mutable", "outer", "public", "quote", "return", "struct", "true", "try",
            "using", "where", "while")
        "d" -> setOf("abstract", "alias", "align", "asm", "assert", "auto", "body",
            "bool", "break", "byte", "case", "cast", "catch", "cdouble", "cent", "cfloat",
            "char", "class", "const", "continue", "creal", "dchar", "debug", "default",
            "delegate", "delete", "deprecated", "do", "double", "else", "enum", "export",
            "extern", "false", "final", "finally", "float", "for", "foreach", "foreach_reverse",
            "function", "goto", "idouble", "if", "ifloat", "immutable", "import", "in",
            "inout", "int", "interface", "invariant", "ireal", "is", "lazy", "long",
            "macro", "mixin", "module", "new", "nothrow", "null", "out", "override",
            "package", "pragma", "private", "protected", "public", "pure", "real",
            "ref", "return", "scope", "shared", "short", "static", "struct", "super",
            "switch", "synchronized", "template", "this", "throw", "true", "try",
            "typeid", "typeof", "ubyte", "ucent", "uint", "ulong", "union", "unittest",
            "ushort", "version", "void", "wchar", "while", "with")
        else -> emptySet()
    }

    private fun types(lang: String): Set<String> = when (lang) {
        "java" -> setOf("boolean", "byte", "char", "double", "float", "int", "long",
            "short", "void", "String", "Object", "Integer", "Long", "Boolean", "Double",
            "Float", "Byte", "Short", "Character", "Class", "List", "Map", "Set",
            "ArrayList", "HashMap", "HashSet", "Optional", "Stream")
        "kt" -> setOf("Boolean", "Byte", "Char", "Double", "Float", "Int", "Long",
            "Short", "String", "Unit", "Nothing", "Any", "Array", "List", "Map", "Set",
            "MutableList", "MutableMap", "MutableSet", "Sequence", "Pair", "Triple",
            "UByte", "UShort", "UInt", "ULong")
        "cpp" -> setOf("bool", "char", "char8_t", "char16_t", "char32_t", "double",
            "float", "int", "long", "short", "signed", "unsigned", "void", "wchar_t",
            "size_t", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t",
            "uint32_t", "uint64_t", "string", "vector", "map", "set", "pair", "tuple",
            "shared_ptr", "unique_ptr", "optional", "variant")
        "cs" -> setOf("bool", "byte", "char", "decimal", "double", "float", "int",
            "long", "object", "sbyte", "short", "string", "uint", "ulong", "ushort",
            "void", "var", "dynamic", "Task", "List", "Dictionary", "IEnumerable",
            "IAsyncEnumerable")
        "py" -> setOf("bool", "bytes", "bytearray", "complex", "dict", "float", "frozenset",
            "int", "list", "memoryview", "object", "range", "set", "slice", "str", "tuple",
            "type", "None", "True", "False", "Exception")
        "ts" -> setOf("boolean", "number", "string", "void", "any", "never", "unknown",
            "null", "undefined", "symbol", "bigint", "object", "Array", "Map", "Set",
            "Promise", "Record", "Partial", "Required", "Readonly", "Pick", "Omit")
        "go" -> setOf("bool", "byte", "complex64", "complex128", "error", "float32",
            "float64", "int", "int8", "int16", "int32", "int64", "rune", "string",
            "uint", "uint8", "uint16", "uint32", "uint64", "uintptr")
        "rs" -> setOf("bool", "char", "f32", "f64", "i8", "i16", "i32", "i64", "i128",
            "isize", "str", "String", "u8", "u16", "u32", "u64", "u128", "usize",
            "Vec", "Option", "Result", "HashMap", "Box", "Rc", "Arc", "Cell", "RefCell")
        "swift" -> setOf("Any", "Bool", "Character", "Codable", "Collection", "Data",
            "Date", "Decodable", "Dictionary", "Double", "Encodable", "Error", "Float",
            "Int", "Int8", "Int16", "Int32", "Int64", "Optional", "Result", "Set",
            "String", "Substring", "UInt", "UInt8", "UInt16", "UInt32", "UInt64",
            "URL", "Void")
        "dart" -> setOf("bool", "double", "Dynamic", "enum", "Function", "int", "List",
            "Map", "Never", "Null", "num", "Object", "Set", "String", "Symbol", "void",
            "Future", "Stream", "Iterable")
        "rb" -> setOf("Array", "Hash", "Integer", "Float", "String", "Symbol")
        "scala" -> setOf("Any", "AnyVal", "Boolean", "Byte", "Char", "Double", "Float",
            "Int", "List", "Long", "Map", "None", "Nothing", "Option", "Seq", "Set",
            "Short", "Some", "String", "Unit", "Vector")
        "jl" -> setOf("Any", "Array", "Bool", "Char", "Complex", "Dict", "Float16",
            "Float32", "Float64", "Function", "Int", "Int8", "Int16", "Int32", "Int64",
            "Int128", "Matrix", "Nothing", "Number", "Real", "Set", "String", "Symbol",
            "Tuple", "UInt8", "UInt16", "UInt32", "UInt64", "Vector")
        "zig" -> setOf("bool", "comptime_float", "comptime_int", "f16", "f32", "f64",
            "f80", "f128", "i8", "i16", "i32", "i64", "i128", "isize", "noreturn",
            "type", "void", "u8", "u16", "u32", "u64", "u128", "usize", "anyerror",
            "anyopaque", "anyframe", "anytype")
        "php" -> setOf("bool", "int", "float", "string", "array", "object", "mixed",
            "void", "null", "callable", "iterable", "static", "self", "parent")
        "nim" -> setOf("bool", "char", "cstring", "float", "float32", "float64", "int",
            "int8", "int16", "int32", "int64", "pointer", "ptr", "string", "uint",
            "uint8", "uint16", "uint32", "uint64", "void")
        "gradle" -> keywords("kt") + keywords("groovy")
        else -> emptySet()
    }

    private fun builtins(lang: String): Set<String> = when (lang) {
        "py" -> setOf("abs", "all", "any", "bin", "bool", "callable", "chr", "classmethod",
            "compile", "complex", "delattr", "dict", "dir", "divmod", "enumerate", "eval",
            "exec", "filter", "float", "format", "frozenset", "getattr", "globals",
            "hasattr", "hash", "help", "hex", "id", "input", "int", "isinstance",
            "issubclass", "iter", "len", "list", "locals", "map", "max", "min", "next",
            "object", "oct", "open", "ord", "pow", "print", "property", "range",
            "repr", "reversed", "round", "set", "setattr", "slice", "sorted",
            "staticmethod", "str", "sum", "super", "tuple", "type", "vars", "zip",
            "__import__", "Exception", "RuntimeError", "ValueError", "TypeError",
            "self", "cls")
        "java" -> setOf("true", "false", "null")
        "kt" -> setOf("true", "false", "null", "this", "super")
        "cpp" -> setOf("true", "false", "nullptr", "NULL", "std", "cout", "cin", "cerr",
            "endl", "printf", "scanf", "main", "size_t")
        "js" -> setOf("console", "document", "window", "Math", "JSON", "Array", "Object",
            "String", "Number", "Boolean", "Date", "RegExp", "Error", "Map", "Set",
            "Promise", "Symbol", "undefined", "NaN", "Infinity", "arguments", "this",
            "parseInt", "parseFloat", "setTimeout", "setInterval", "require", "module",
            "exports", "process", "Buffer", "global")
        "ts" -> setOf("console", "document", "window", "Math", "JSON", "Array", "Object",
            "String", "Number", "Boolean", "Date", "RegExp", "Error", "Map", "Set",
            "Promise", "Symbol")
        "go" -> setOf("nil", "true", "false", "append", "cap", "close", "complex", "copy",
            "delete", "imag", "len", "make", "new", "panic", "print", "println", "real",
            "recover", "iota")
        "rs" -> setOf("true", "false", "Some", "None", "Ok", "Err", "println", "print",
            "format", "vec", "panic", "unreachable")
        "rb" -> setOf("puts", "gets", "print", "p", "require", "include", "extend",
            "attr_accessor", "attr_reader", "attr_writer", "raise", "rescue", "__FILE__",
            "__LINE__", "ENV")
        "lua" -> setOf("print", "ipairs", "pairs", "next", "rawget", "rawset", "rawlen",
            "select", "tonumber", "tostring", "type", "require", "module", "setmetatable",
            "getmetatable", "error", "assert", "pcall", "xpcall", "dofile", "load",
            "loadfile", "self")
        "sql" -> setOf("COUNT", "SUM", "AVG", "MAX", "MIN", "COALESCE", "NULLIF",
            "CAST", "CONVERT", "UPPER", "LOWER", "TRIM", "LENGTH", "SUBSTRING",
            "REPLACE", "NOW", "CURRENT_TIMESTAMP", "DATE", "TIME", "ABS", "ROUND",
            "CEIL", "FLOOR", "CONCAT", "LEFT", "RIGHT", "MID")
        "sh" -> setOf("echo", "export", "read", "source", "exit", "return", "cd", "ls",
            "pwd", "mkdir", "rm", "cp", "mv", "cat", "grep", "awk", "sed", "chmod",
            "chown", "kill", "ps", "date", "dirname", "basename", "test", "true", "false")
        "dart" -> setOf("print", "true", "false", "null")
        "swift" -> setOf("print", "true", "false", "nil")
        "scala" -> setOf("true", "false", "null", "println", "print")
        "groovy" -> setOf("true", "false", "null", "println", "print")
        else -> emptySet()
    }

    // ==================== Comment Syntax per Language ====================
    private data class CommentSyntax(val line: String?, val blockStart: String?, val blockEnd: String?)

    private fun comment(lang: String): CommentSyntax = when (lang) {
        "cpp", "java", "kt", "cs", "js", "ts", "go", "rs", "swift", "dart", "groovy",
        "scala", "d", "zig", "nim", "jl", "hx" -> CommentSyntax("//", "/*", "*/")
        "py", "rb", "sh", "pl", "r", "yaml", "toml", "ps1", "bat", "ini", "conf",
        "make", "cmake", "vim" -> CommentSyntax("#", null, null)
        "lua" -> CommentSyntax("--", "--[[", "]]")
        "sql" -> CommentSyntax("--", "/*", "*/")
        "html", "xml", "svg", "wxml" -> CommentSyntax(null, "<!--", "-->")
        "css", "scss", "less" -> CommentSyntax(null, "/*", "*/")
        "tex" -> CommentSyntax("%", null, null)
        "hs" -> CommentSyntax("--", "{-", "-}")
        "asm" -> CommentSyntax(";", null, null)
        "vb" -> CommentSyntax("'", null, null)
        "ml" -> CommentSyntax(null, "(*", "*)")
        "bat" -> CommentSyntax("rem ", null, null)
        "pas" -> CommentSyntax("//", "{", "}")
        else -> CommentSyntax("//", "/*", "*/")
    }

    // ==================== Main Highlight Entry Point ====================

    fun highlight(code: String, extension: String): AnnotatedString {
        val lang = if (extension == "hex") "hex" else langFor(extension.lowercase())
        return buildAnnotatedString {
            if (lang == "hex") {
                append(code)
                return@buildAnnotatedString
            }
            if (lang.isEmpty()) {
                append(code)
                return@buildAnnotatedString
            }
            val cmt = comment(lang)
            val kw = keywords(lang)
            val tp = types(lang)
            val bi = builtins(lang)
            val lines = code.split("\n")
            var inBlockComment = false
            lines.forEachIndexed { i, line ->
                val (outInBlock, annotated) = highlightLine(line, lang, kw, tp, bi, cmt, inBlockComment)
                inBlockComment = outInBlock
                append(annotated)
                if (i < lines.size - 1) append("\n")
            }
        }
    }

    // ==================== Line Highlighting ====================

    private fun highlightLine(
        line: String, lang: String,
        keywords: Set<String>, types: Set<String>, builtins: Set<String>,
        cmt: CommentSyntax, inBlock: Boolean
    ): Pair<Boolean, AnnotatedString> {

        // 如果处于块注释中
        if (inBlock) {
            val endIdx = cmt.blockEnd?.let { line.indexOf(it) }
            return if (endIdx != null) {
                val remaining = if (endIdx + (cmt.blockEnd?.length ?: 2) < line.length)
                    line.substring(endIdx + (cmt.blockEnd?.length ?: 2)) else null
                Pair(false, buildAnnotatedString {
                    withStyle(SpanStyle(color = cComment)) { append(line.substring(0, endIdx + (cmt.blockEnd?.length ?: 2))) }
                    if (remaining != null) {
                        val (_, rest) = highlightLine(remaining, lang, keywords, types, builtins, cmt, false)
                        append(rest)
                    }
                })
            } else {
                Pair(true, buildAnnotatedString { withStyle(SpanStyle(color = cComment)) { append(line) } })
            }
        }

        if (line.isEmpty()) return Pair(false, buildAnnotatedString { })

        return Pair(false, buildAnnotatedString {
            val trimmed = line.trimStart()
            val leadingWs = line.length - trimmed.length
            if (leadingWs > 0) append(line.substring(0, leadingWs))

            // 行注释检查
            val hsComment = lang in setOf("py", "rb", "sh", "pl", "r", "yaml", "toml", "ps1", "bat", "ini", "conf", "make", "cmake", "vim")
            val isLineComment = when {
                cmt.line != null && trimmed.startsWith(cmt.line) -> true
                hsComment && trimmed.startsWith("#") -> true
                lang == "tex" && trimmed.startsWith("%") -> true
                lang == "vb" && trimmed.startsWith("'") -> true
                else -> false
            }
            if (isLineComment) {
                withStyle(SpanStyle(color = cComment)) { append(trimmed) }
                return@buildAnnotatedString
            }

            tokenizeHere(trimmed, lang, keywords, types, builtins, cmt)
        })
    }

    // ==================== Tokenizer ====================

    @Suppress("DEPRECATION")
    private fun AnnotatedString.Builder.tokenizeHere(
        text: String, lang: String,
        keywords: Set<String>, types: Set<String>, builtins: Set<String>,
        cmt: CommentSyntax
    ) {
        var i = 0
        while (i < text.length) {
            when {
                // 块注释
                cmt.blockStart != null && text.startsWith(cmt.blockStart, i) -> {
                    val endTag = cmt.blockEnd ?: "*/"
                    val end = text.indexOf(endTag, i + cmt.blockStart.length)
                    if (end > 0) {
                        withStyle(SpanStyle(color = cComment)) { append(text.substring(i, end + endTag.length)) }
                        i = end + endTag.length
                    } else {
                        withStyle(SpanStyle(color = cComment)) { append(text.substring(i)) }
                        return
                    }
                }
                // 文档注释 /** or ///
                (lang in setOf("java","kt","cpp","cs","js","ts","go","rs","swift","dart") || cmt.line == "//") &&
                (text.startsWith("/**", i) || text.startsWith("///", i) || text.startsWith("//!", i)) -> {
                    val isBlock = text.startsWith("/**", i)
                    val end = if (isBlock) {
                        val e = text.indexOf("*/", i + 3)
                        if (e > 0) e + 2 else text.length
                    } else {
                        val e = text.indexOf('\n', i)
                        if (e > 0) e else text.length
                    }
                    withStyle(SpanStyle(color = cDocComment)) { append(text.substring(i, end)) }
                    i = end
                }
                // 行注释 (行内)
                cmt.line != null && text.startsWith(cmt.line, i) &&
                (i == 0 || text[i-1].isWhitespace() || text[i-1] in "({") -> {
                    withStyle(SpanStyle(color = cComment)) { append(text.substring(i)) }
                    return
                }
                // """ / ''' 三引号字符串
                text.startsWith("\"\"\"", i) || text.startsWith("'''", i) -> {
                    val q = text.substring(i, i + 3)
                    val end = text.indexOf(q, i + 3)
                    val style = SpanStyle(color = if (q == "\"\"\"") cStringRaw else cString)
                    withStyle(style) {
                        append(q)
                        if (end > 0) { append(text.substring(i + 3, end)); append(q); i = end + 3 }
                        else { append(text.substring(i + 3)); i = text.length }
                    }
                }
                // 双引号字符串
                text[i] == '"' -> {
                    val end = findStringEnd(text, i, '"')
                    val style = SpanStyle(color = if (lang in setOf("js","ts") && text.substring(i, end).contains("\${")) cStringRaw else cString)
                    withStyle(style) { append(text.substring(i, end)) }
                    i = end
                }
                // 单引号字符串
                text[i] == '\'' && lang != "java" -> {
                    val end = findStringEnd(text, i, '\'')
                    withStyle(SpanStyle(color = cString)) { append(text.substring(i, end)) }
                    i = end
                }
                // 反引号
                text[i] == '`' && lang in setOf("go","js","ts","kt") -> {
                    val end = findStringEnd(text, i, '`')
                    withStyle(SpanStyle(color = cBacktick)) { append(text.substring(i, end)) }
                    i = end
                }
                // 数字
                text[i].isDigit() -> {
                    val start = i
                    when {
                        text.startsWith("0x") || text.startsWith("0X") -> i += 2
                        text.startsWith("0b") || text.startsWith("0B") -> i += 2
                        text.startsWith("0o") || text.startsWith("0O") -> i += 2
                    }
                    while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == '_' ||
                        text[i] in "abcdefABCDEF" || text[i] == 'e' || text[i] == 'E')) i++
                    withStyle(SpanStyle(color = cNumber)) { append(text.substring(start, i)) }
                }
                // 操作符
                text[i] in "+-*/%=<>!&|^~?:" -> {
                    val start = i
                    while (i < text.length && text[i] in "+-*/%=<>!&|^~?:.") i++
                    withStyle(SpanStyle(color = cOperator)) { append(text.substring(start, i)) }
                }
                // 注解
                text[i] == '@' && lang in setOf("java","kt","cs","rb","ts","swift","dart","scala","groovy","py") -> {
                    val start = i; i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '.' || text[i] == '_')) i++
                    withStyle(SpanStyle(color = if (lang == "py") cDecorator else cAnnotation)) { append(text.substring(start, i)) }
                }
                // C/C# 预处理器
                text[i] == '#' && lang in setOf("cpp","cs","d") -> {
                    val start = i; i++
                    while (i < text.length && (text[i].isLetter() || text[i] == '_')) i++
                    withStyle(SpanStyle(color = cPreprocessor)) { append(text.substring(start, i)) }
                }
                // 标识符
                text[i].isLetter() || text[i] == '_' -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$')) i++
                    val word = text.substring(start, i)
                    val followedByParen = i < text.length && text[i] == '('
                    when {
                        word in keywords -> {
                            val c = if (lang in setOf("py","rb","php","sh","bat","pl","r","jl","zig","nim","lisp","v","vhdl","vb")) cKeywordBold else cKeyword
                            withStyle(SpanStyle(color = c, fontWeight = FontWeight.Bold)) { append(word) }
                        }
                        word in types -> withStyle(SpanStyle(color = cType, fontWeight = FontWeight.Bold)) { append(word) }
                        word in builtins -> withStyle(SpanStyle(color = cBuiltin)) { append(word) }
                        followedByParen -> withStyle(SpanStyle(color = cFunction, fontWeight = FontWeight.Bold)) { append(word) }
                        word in setOf("true","false","null","nil","None","nullptr","undefined") -> withStyle(SpanStyle(color = cConstant)) { append(word) }
                        else -> append(word)
                    }
                }
                // 其余字符
                else -> { append(text[i]); i++ }
            }
        }
    }

    private fun findStringEnd(text: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length) { i += 2; continue }
            if (text[i] == quote) return i + 1
            i++
        }
        return text.length.coerceAtMost(start + 1)
    }

}
