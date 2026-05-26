package com.ycy.aiapplication.parse.toolkit;

/**
 * 文本清理工具类
 * <p>
 * 注：
 * replace：替换所有被替换字符串
 * replaceAll：支持正则的替换（并不是支持正则版本的字符串替换，使用例如+等符号的含义完全不同）
 */
public final class TextCleanupUtil {

    private TextCleanupUtil() {
    }

    /**
     * 清理文本内容
     * 执行以下清理操作：
     * 1.移除BOM标记（\uFEFF）
     *  什么是BOM：
     *      Byte Order  Mark:文本开头的特殊字节，用于识别文件编码格式&字节序（文件开头的不可见字节）
     * 2.移除行尾多余的空格和制表符
     * 3.压缩连续空行（3个以上压缩为两个）
     * 4.去除首尾空白
     *
     * @param text 原始文本
     * @return 清理之后的文本
     */
    public static String cleanup(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text
                // 移除 BOM 标记
                .replace("\uFEFF", "")
                //[字符]：匹配方括号内任意字符
                //  当前有两个字符：空格和制表符
                //+标识匹配前面的字符集合1次或者多次（至少一次，上不封顶）；常见的还有*表示0/多次，？表示0或者1次
                //当前正则表达式含义：将所有由空格和制表符组成且后面紧跟换行符的连续序列替换为单个换行符
                .replaceAll("[ \\t]+\\n", "\n")
                //{N}表示恰好N次，{N，M}：最少N次，最多M次；{N，}表示至少N次
                //将多（≥3）换行符转换为双换行符
                .replaceAll("\\n{3,}", "\n\n")
                // 去除首尾空白
                .trim();
    }

    /**
     * 清理文本内容（自定义规则）
     *
     * @param text                原始文本
     * @param removeBOM           是否移除 BOM
     * @param trimTrailingSpaces  是否移除行尾空格
     * @param compressEmptyLines  是否压缩空行
     * @param maxConsecutiveLines 最多保留的连续空行数
     * @return 清理后的文本
     */
    public static String cleanup(String text,
                                 boolean removeBOM,
                                 boolean trimTrailingSpaces,
                                 boolean compressEmptyLines,
                                 int maxConsecutiveLines) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;

        if (removeBOM) {
            result = result.replace("\uFEFF", "");
        }

        if (trimTrailingSpaces) {
            result = result.replaceAll("[ \\t]+\\n", "\n");
        }

        if (compressEmptyLines && maxConsecutiveLines > 0) {
            //大于最大保留行数的进行替换
            String pattern = "\\n{" + (maxConsecutiveLines + 1) + ",}";
            //最大保留的换行
            String replacement = "\n".repeat(maxConsecutiveLines);
            result = result.replaceAll(pattern, replacement);
        }

        return result.trim();
    }
}