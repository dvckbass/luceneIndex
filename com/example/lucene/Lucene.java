
package com.example.lucene;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;

import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;

/**
 * Dient als Klasse für Web Indexierung, die Konfiguration benötigt
 * 
 * @author Ardilla Latifasari
 *
 */

public class Lucene {

	public static final String PROPERTY_INDEX_DIRECTORY = "indexDir";
	public static final String PROPERTY_DATA_DIRECTORY = "dataDir";
	public static final String PROPERTY_FILE = "config.properties";

	private String indexPath;
	private String dataPath;
    boolean create = true;
	private static int count = 0;

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		Lucene lucene = new Lucene();
		lucene.loadProp();
		lucene.indexing();
	}

	public void loadProp() throws IOException {
		Properties prop = new Properties();
		InputStream input = null;

		input = new FileInputStream(PROPERTY_FILE);
		prop.load(input);

		indexPath = prop.getProperty(PROPERTY_INDEX_DIRECTORY);
		dataPath = prop.getProperty(PROPERTY_DATA_DIRECTORY);
	}

	public void indexing() throws IOException {
	    final Path docDir = Paths.get(dataPath);
		Date start = new Date();
		System.out.println("Indexing to directory '" + indexPath + "'...");

		Directory dir = FSDirectory.open(Paths.get(indexPath));
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

		if (create) {
			// Create a new index in the directory, removing any
			// previously indexed documents:
			iwc.setOpenMode(OpenMode.CREATE);
		} else {
			// Add new documents to an existing index:
			iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
		}

		// Optional: for better indexing performance, if you
		// are indexing many documents, increase the RAM
		// buffer. But if you do this, increase the max heap
		// size to the JVM (eg add -Xmx512m or -Xmx1g):
		//
		// iwc.setRAMBufferSizeMB(256.0);

		IndexWriter writer = new IndexWriter(dir, iwc);
		indexDocs(writer, docDir);

		// NOTE: if you want to maximize search performance,
		// you can optionally call forceMerge here. This can be
		// a terribly costly operation, so generally it's only
		// worth it when your index is relatively static (ie
		// you're done adding documents to it):
		//
		// writer.forceMerge(1);

		writer.close();

		Date end = new Date();
		System.out.println("Number of files: " + count);
		System.out.println(end.getTime() - start.getTime() + " total milliseconds");
	}

	/**
	 * Indexes the given file using the given writer, or if a directory is given,
	 * recurses over files and directories found under the given directory.
	 * 
	 * NOTE: This method indexes one document per input file. This is slow. For good
	 * throughput, put multiple documents into your input file(s). An example of
	 * this is in the benchmark module, which can create "line doc" files, one
	 * document per line, using the <a href=
	 * "../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
	 * >WriteLineDocTask</a>.
	 * 
	 * @param writer Writer to the index where the given file/dir info will be
	 *               stored
	 * @param path   The file to index, or the directory to recurse into to find
	 *               files to index
	 * @throws IOException If there is a low-level I/O error
	 */
	static void indexDocs(final IndexWriter writer, Path path) throws IOException {
		if (Files.isDirectory(path)) {
			File file = path.toFile();
			Collection<File> files = FileUtils.listFiles(file, null, false);
			for (File file2 : files) {
				String ext = FilenameUtils.getExtension(file2.toString());
				if ((ext.equals("htm") || ext.equals("html")) && file2.length() > 0) {
					count++;
					// System.out.println(file2.getName());
					indexDoc(writer, file2.toPath());
				}
			}
		}
	}

	/** Indexes a single document */
	static void indexDoc(IndexWriter writer, Path file) throws IOException {
		try (InputStream stream = Files.newInputStream(file)) {
			// make a new, empty document
			Document doc = new Document();

			System.out.println("Count: " + count);
			// Add creation time
			BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
			Field dateField = new StringField("date", attr.creationTime().toString(), Field.Store.YES);
			doc.add(dateField);
			System.out.println("date: " + attr.creationTime().toString());

			// Add the size of the file
			File fileFile = file.toFile();
			long sizeFile = fileFile.length();
			Field sizeField = new StringField("size", Long.toString(sizeFile), Field.Store.YES);
			doc.add(sizeField);
			System.out.println("size: " + Long.toString(sizeFile));

			// Add file name
			String fileName = fileFile.getName();
			Field fileNameField = new StringField("fileName", fileName, Field.Store.YES);
			doc.add(fileNameField);
			System.out.println("file name: " + fileName);

			// Add the author
			// Check if it has meta tag for author
			org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(new File(file.toString()), "utf-8");
			if (jsoupDoc.select("meta[name=author]").size() > 0) {
				String author = jsoupDoc.select("meta[name=author]").first().attr("content");
				Field authorField = new StringField("author", author, Field.Store.YES);
				doc.add(authorField);
				System.out.println("author: " + author);
			}

			// Add the title
			String title = jsoupDoc.title();
			Field titleField = new StringField("title", title, Field.Store.YES);
			doc.add(titleField);
			System.out.println("title: " + title);

			// Change file to text
			Scanner scanner = new Scanner(fileFile);
			String text = scanner.useDelimiter("\\A").next();
			scanner.close();

			// Parse the string
			org.jsoup.nodes.Document encodingDoc = Jsoup.parse(text);

			Charset charset = encodingDoc.charset();
			String encoding = charset.name();

			if (encoding != null) {
				Field encodingField = new StringField("encoding", encoding, Field.Store.YES);
				System.out.println("encoding: " + encoding);
				doc.add(encodingField);
			}

			// Add the contents of the file to a field named "contents". Specify a Reader,
			// so that the text of the file is tokenized and indexed, but not stored.
			// Note that FileReader expects the file to be in UTF-8 encoding.
			// If that's not the case searching for special characters will fail.
			String body = jsoupDoc.body().text();
			body = validate(body);

			// check body to txt
			BufferedWriter buffWriter = new BufferedWriter(new FileWriter("body.txt", true));
			buffWriter.write(body + " ");
			// writer.close();
			buffWriter.close();

			/*
			 * //put body as strings String[] terms = body.split("\\s+"); for(String term:
			 * terms) { BufferedWriter termWriter = new BufferedWriter(new
			 * FileWriter("terms.txt",true)); termWriter.write(term + " ");
			 * termWriter.close(); doc.add(new StringField("terms", term.toLowerCase(),
			 * Field.Store.YES)); }
			 */

			// change String to InputStream for body
			InputStream inputStream = new ByteArrayInputStream(body.getBytes(Charset.forName("UTF-8")));
			doc.add(new TextField("contents",
					new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))));

			if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
				// New index, so we just add the document (no old document can be there):
				System.out.println("adding " + file + '\n');
				writer.addDocument(doc);
			} else {
				// Existing index (an old copy of this document may have been indexed) so
				// we use updateDocument instead to replace the old one matching the exact
				// path, if present:
				System.out.println("updating " + file);
				writer.updateDocument(new Term("path", file.toString()), doc);
			}

		}
	}

	// Validierung von Strings, ob die nicht mit Zahlen anfangen
	static private String validate(String string) throws IOException {
		String tmp = "";
		
		String[] splitString = (string.split("\\s+"));
		for (String s : splitString) {
			boolean found = false;
			if (s.matches("^[a-zA-Z]+.*[a-zA-Z0-9]$")) {
				if(s.contains(":") && !(s.matches("^http+.*")) ) {
					found = true;
					System.out.println("String: " + s);
					String parts[] = s.split("\\:");
					for(int i=0; i<parts.length;i++) {
						System.out.println("parts[" + i + "]: " + parts[i]);
						tmp += parts[i] + " ";
					}
				}
				if(found != true) {
				tmp += s + " ";
				}
			}
			
		}
		return tmp;
	}
}
