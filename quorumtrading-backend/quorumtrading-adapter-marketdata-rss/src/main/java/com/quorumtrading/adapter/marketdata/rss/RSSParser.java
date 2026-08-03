package com.quorumtrading.adapter.marketdata.rss;

import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class RSSParser {

    private static final Logger logger = Logger.getLogger(RSSParser.class);

    // RFC 822 date format used by RSS 2.0
    private static final DateTimeFormatter RFC822_FORMATTER =
        DateTimeFormatter.RFC_1123_DATE_TIME;

    // ISO 8601 format used by Atom
    private static final DateTimeFormatter ISO8601_FORMATTER =
        DateTimeFormatter.ISO_DATE_TIME;

    public List<Map<String, Object>> parse(InputStream inputStream, String sourceName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);

            Element root = doc.getDocumentElement();
            String rootTag = root.getTagName();

            if ("rss".equals(rootTag)) {
                return parseRSS2(doc, sourceName);
            } else if ("feed".equals(rootTag)) {
                return parseAtom(doc, sourceName);
            } else {
                logger.warnf("Unknown feed format: %s", rootTag);
                return Collections.emptyList();
            }

        } catch (Exception e) {
            logger.errorf(e, "Failed to parse RSS/Atom feed from %s", sourceName);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseRSS2(Document doc, String sourceName) {
        List<Map<String, Object>> items = new ArrayList<>();

        NodeList itemNodes = doc.getElementsByTagName("item");

        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element item = (Element) itemNodes.item(i);

            String title = getTextContent(item, "title");
            String description = getTextContent(item, "description");
            String link = getTextContent(item, "link");
            String pubDate = getTextContent(item, "pubDate");

            if (title == null || title.isBlank()) {
                continue;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("title", title);
            data.put("description", description != null ? description : "");
            data.put("link", link != null ? link : "");
            data.put("source", sourceName);

            // Parse date and convert to ISO-8601 string for JSON compatibility
            Instant timestamp = parseRFC822Date(pubDate);
            if (timestamp != null) {
                data.put("pubDate", timestamp.toString());
            } else {
                data.put("pubDate", Instant.now().toString());
            }

            items.add(data);
        }

        return items;
    }

    private List<Map<String, Object>> parseAtom(Document doc, String sourceName) {
        List<Map<String, Object>> items = new ArrayList<>();

        NodeList entryNodes = doc.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry");

        for (int i = 0; i < entryNodes.getLength(); i++) {
            Element entry = (Element) entryNodes.item(i);

            String title = getTextContentNS(entry, "http://www.w3.org/2005/Atom", "title");
            String summary = getTextContentNS(entry, "http://www.w3.org/2005/Atom", "summary");
            String updated = getTextContentNS(entry, "http://www.w3.org/2005/Atom", "updated");

            // Get link href attribute
            String link = "";
            NodeList linkNodes = entry.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "link");
            if (linkNodes.getLength() > 0) {
                Element linkElement = (Element) linkNodes.item(0);
                link = linkElement.getAttribute("href");
            }

            if (title == null || title.isBlank()) {
                continue;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("title", title);
            data.put("description", summary != null ? summary : "");
            data.put("link", link);
            data.put("source", sourceName);

            // Parse date and convert to ISO-8601 string for JSON compatibility
            Instant timestamp = parseISO8601Date(updated);
            if (timestamp != null) {
                data.put("pubDate", timestamp.toString());
            } else {
                data.put("pubDate", Instant.now().toString());
            }

            items.add(data);
        }

        return items;
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private String getTextContentNS(Element parent, String namespace, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private Instant parseRFC822Date(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr, RFC822_FORMATTER);
            return zdt.toInstant();
        } catch (DateTimeParseException e) {
            logger.debugf("Failed to parse RFC822 date: %s", dateStr);
            return null;
        }
    }

    private Instant parseISO8601Date(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr, ISO8601_FORMATTER);
            return zdt.toInstant();
        } catch (DateTimeParseException e) {
            logger.debugf("Failed to parse ISO8601 date: %s", dateStr);
            return null;
        }
    }
}
