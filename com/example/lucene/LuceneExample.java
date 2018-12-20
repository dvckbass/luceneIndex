package com.example.lucene;




/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
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
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;

/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class LuceneExample {
  
  private LuceneExample() {}
  
  public static int count = 0;
  
  /** Index all text files under a directory. */
  public static void main(String[] args) {
	  /*
    String usage = "java org.apache.lucene.demo.IndexFiles"
                 + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                 + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                 + "in INDEX_PATH that can be searched with SearchFiles";
                 */
    String indexPath = "";
    String docsPath = "";
    boolean create = true;
    
    /*
    for(int i=0;i<args.length;i++) {
      if ("-index".equals(args[i])) {
        indexPath = args[i+1];
        i++;
      } else if ("-docs".equals(args[i])) {
        docsPath = args[i+1];
        i++;
      } else if ("-update".equals(args[i])) {
        create = false;
      }
    }
    */
    
    ///////////////////////////////////////////////////////////
    //load the properties for index and data directories//////
    
    Properties prop = new Properties();
	InputStream input = null;

	try {

		input = new FileInputStream("config.properties");

		// load a properties file
		prop.load(input);

		// get the property value and print it out
		indexPath = prop.getProperty("indexDir");
		docsPath = prop.getProperty("dataDir");

		System.out.println("### indexPath: " + indexPath);
	} catch (IOException ex) {
		ex.printStackTrace();
	} finally {
		if (input != null) {
			try {
				input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	//////////////////////////////////////////////////////
	
	
    if (docsPath == null) {
     // System.err.println("Usage: " + usage);
      System.exit(1);
    }

    final Path docDir = Paths.get(docsPath);
    if (!Files.isReadable(docDir)) {
      System.out.println("Document directory '" +docDir.toAbsolutePath()+ "' does not exist or is not readable, please check the path");
      System.exit(1);
    }
    
    Date start = new Date();
    try {
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
      // buffer.  But if you do this, increase the max heap
      // size to the JVM (eg add -Xmx512m or -Xmx1g):
      //
      // iwc.setRAMBufferSizeMB(256.0);

      IndexWriter writer = new IndexWriter(dir, iwc);
      indexDocs(writer, docDir);

      // NOTE: if you want to maximize search performance,
      // you can optionally call forceMerge here.  This can be
      // a terribly costly operation, so generally it's only
      // worth it when your index is relatively static (ie
      // you're done adding documents to it):
      //
      // writer.forceMerge(1);

      writer.close();

      Date end = new Date();
      System.out.println("Number of files: " + count);
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
       "\n with message: " + e.getMessage());
    }
  }

  /**
   * Indexes the given file using the given writer, or if a directory is given,
   * recurses over files and directories found under the given directory.
   * 
   * NOTE: This method indexes one document per input file.  This is slow.  For good
   * throughput, put multiple documents into your input file(s).  An example of this is
   * in the benchmark module, which can create "line doc" files, one document per line,
   * using the
   * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
   * >WriteLineDocTask</a>.
   *  
   * @param writer Writer to the index where the given file/dir info will be stored
   * @param path The file to index, or the directory to recurse into to find files to index
   * @throws IOException If there is a low-level I/O error
   */
  static void indexDocs(final IndexWriter writer, Path path) throws IOException {
    if (Files.isDirectory(path)) {
      File file = path.toFile();
      Collection<File> files = FileUtils.listFiles(file, null, false);     
  	  for(File file2 : files){
  		String ext = FilenameUtils.getExtension(file2.toString());
  		if((ext.equals("htm") || ext.equals("html")) && file2.length() > 0) {
  			count++;
  			//System.out.println(file2.getName());
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
      //Add creation time
      BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
      Field dateField = new StringField("date", attr.creationTime().toString(), Field.Store.YES);
      doc.add(dateField);
      System.out.println("date: " +  attr.creationTime().toString());
      
      
      //Add the size of the file
      File fileFile = file.toFile();
      long sizeFile = fileFile.length();
      Field sizeField = new StringField("size", Long.toString(sizeFile), Field.Store.YES);
      doc.add(sizeField);
      System.out.println("size: " +  Long.toString(sizeFile));
      
      //Add file name
      String fileName = fileFile.getName();
      Field fileNameField = new StringField("fileName", fileName, Field.Store.YES);
      doc.add(fileNameField);
      System.out.println("file name: " + fileName);
      
      
      //Add the author
      //Check if it has meta tag for author
      org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(new File(file.toString()), "utf-8"); 
      if(jsoupDoc.select("meta[name=author]").size() > 0 ) {
	      String author = jsoupDoc.select("meta[name=author]").first().attr("content");
	      Field authorField = new StringField("author", author, Field.Store.YES);
	      doc.add(authorField);
	      System.out.println("author: " +  author);
      }
           
      
      //Add the title     
      String title = jsoupDoc.title();
      Field titleField = new StringField("title", title, Field.Store.YES);
      doc.add(titleField);
      System.out.println("title: " +  title);
      
      //Change file to text
      Scanner scanner = new Scanner(fileFile);
      String text = scanner.useDelimiter("\\A").next();
      scanner.close();
      
      //Parse the string
      org.jsoup.nodes.Document encodingDoc = Jsoup.parse(text);
      
      Charset charset = encodingDoc.charset();
      String encoding = charset.name();
      
      if(encoding != null) {
    	  Field encodingField = new StringField("encoding", encoding, Field.Store.YES);
    	  System.out.println("encoding: " +  encoding);
    	  doc.add(encodingField);
      }
     
      
      
      
      // Add the contents of the file to a field named "contents".  Specify a Reader,
      // so that the text of the file is tokenized and indexed, but not stored.
      // Note that FileReader expects the file to be in UTF-8 encoding.
      // If that's not the case searching for special characters will fail.
      String body = jsoupDoc.body().text();
      body = validate(body);
      
      //check body to txt
      BufferedWriter buffWriter = new BufferedWriter(new FileWriter("body.txt",true));
      buffWriter.write(body + " ");
      //writer.close();
      buffWriter.close();
      
      /*
      //put body as strings
     String[] terms = body.split("\\s+");
     for(String term: terms) {
    	 BufferedWriter termWriter = new BufferedWriter(new FileWriter("terms.txt",true));
    	 termWriter.write(term + " ");
    	 termWriter.close();
    	 doc.add(new StringField("terms", term.toLowerCase(), Field.Store.YES));
     }
     */
      
      //change String to InputStream for body
      InputStream inputStream = new ByteArrayInputStream(body.getBytes(Charset.forName("UTF-8")));           
      doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))));
      
      
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
  
  //Validierung von Strings, ob die nicht mit Zahlen anfangen
 static private String validate(String string) throws IOException {
	 String tmp = "";
	 String[] splitString = (string.split("\\s+"));
	 for (String s : splitString) {
     	if(s.matches("^[a-zA-Z]+.*[a-zA-Z0-9]$")) {
     		tmp += s + " ";
     	}
     }
	 return tmp;
  }
  
}

 
