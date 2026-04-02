package com.ycy.aiapplication.parse.parser.impl;

import com.ycy.aiapplication.framework.exception.ServiceException;
import com.ycy.aiapplication.parse.common.ParseResult;
import com.ycy.aiapplication.parse.common.ParserType;
import com.ycy.aiapplication.parse.parser.DocumentParser;
import com.ycy.aiapplication.parse.toolkit.TextCleanupUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Apache Tika 文档解析器
 * <p>
 * 支持多种文档格式：PDF、Word、Excel、PPT、HTML、XML 等
 * 使用 Apache Tika 库进行文档解析和文本提取
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    //TIKA自动解析器，根据文件类型选择合适的解析器
    private static final AutoDetectParser PARSER = new AutoDetectParser();
    //PDF解析器配置对象
    private static final PDFParserConfig PDF_PARSER_CONFIG = new PDFParserConfig();

    //PDF节解析配置
    static {
        PDF_PARSER_CONFIG.setExtractInlineImages(false);
        PDF_PARSER_CONFIG.setExtractUniqueInlineImagesOnly(true);
    }

    @Override
    public String getParserType() {
        return ParserType.TIKA.getType();
    }

    @Override
    public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            return ParseResult.ofText("");
        }
        //字节数组抓换位字节输入流
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            //解析
            String text = parseText(inputStream, mimeType, null);
            //返回解析结果
            return ParseResult.ofText(TextCleanupUtil.cleanup(text));
        } catch (Exception ex) {
            log.error("Tika parse failed, mimeType={}", mimeType, ex);
            throw new ServiceException("Document parse failed: " + ex.getMessage());
        }
    }

    @Override
    public String extractText(InputStream stream, String fileName) {
        try {
            String text = parseText(stream, null, fileName);
            return TextCleanupUtil.cleanup(text);
        } catch (Exception ex) {
            log.error("Extract text failed from file: {}", fileName, ex);
            throw new ServiceException("Extract text failed: " + fileName);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && !mimeType.startsWith("text/markdown");
    }

    private String parseText(InputStream stream, String mimeType, String fileName) throws Exception {
        //创建元数据类
        Metadata metadata = new Metadata();
        //存在MIME类型，放入元数据，帮助TIKA更准确选择解析器
        if (mimeType != null && !mimeType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, mimeType);
        }
        //存在文件名，放入元数据帮助TIKA选择解析器
        if (fileName != null && !fileName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        //创建内容处理器，-1表示不限制输出长度，提起全部文本
        ContentHandler handler = new BodyContentHandler(-1);
        //创建解析上下文，并将配置放入上下文
        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, PDF_PARSER_CONFIG);
        //执行解析
        PARSER.parse(stream, handler, metadata, parseContext);
        //返回提取文本
        return handler.toString();
    }
}
