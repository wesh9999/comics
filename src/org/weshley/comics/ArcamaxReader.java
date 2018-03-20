package org.weshley.comics;

import org.ini4j.Ini;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ArcamaxReader
   extends ComicsReader
{
   private static String ARCAMAX_URL = "http://www.arcamax.com";

   private String _url = ARCAMAX_URL;


   public ArcamaxReader()
   {
   }

// FIXME - fetch list of comics from this source and create ini file?
/*
   public static void generateIniFile()
      throws ComicsException
   {
      try
      {
         System.out.println("# general reader properties.  class is required");
         System.out.println("[reader]");
         System.out.println("class = org.weshley.comics.GoComicsReader");
         System.out.println("url = " + GO_COMICS_URL);
         System.out.println("label = Go Comics");
         System.out.println("");
         System.out.println("# list of comics provided by this reader.  entries are ID = LABEL");
         System.out.println("[comics]");

         Document doc = Jsoup.connect(GO_COMICS_LIST_URL).get();
         Elements divs = doc.select("div.media-body");
         for(Element e : divs)
         {
            String label = e.select("h4.media-heading").first().ownText();
            Element linkElem = e.parent().parent();
            String id = linkElem.attributes().get("href").substring(1);
            System.out.println(id + " = " + label);
         }
      }
      catch(Exception ex)
      {
         throw new ComicsException("Error getting all comics from GoComics", ex);
      }

   }
*/

   public void initializeFromConfig(Ini ini)
   {
      super.initializeFromConfig(ini);
      Ini.Section readerConfig = ini.get("reader");
      _url = readerConfig.get("url");
      if(null == _url)
         _url = ARCAMAX_URL;
      if(_url.endsWith("/"))
         _url = _url.substring(0, _url.length() - 1);
   }


   @Override
   public byte[] getImageData(String comicName, Date dt)
      throws ComicsException
   {
      try
      {
//         DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
//         String comicUri = comicName + "/" + dateFormat.format(dt);
// FIXME - what is the arcamax date format?
         String comicUri = "/thefunnies/" + comicName;
         String src = getImageSource(comicUri);
System.out.println("+++src=[" + src + "]");
         return getImageDataInternal(src);
      }
      catch(Exception ex)
      {
         throw new ComicsException(ex);
      }
   }

   private byte[] getImageDataInternal(String imageSrc)
      throws IOException
   {
      URL url = new URL(_url + imageSrc);
      // FIXME - remove debug statements?
      debug("Reading image data from " + url);

      URLConnection conn = url.openConnection();
// FIXME - hardcoded timeout? retry somehow?
      conn.setReadTimeout(60*1000);  // 60s timeout
      conn.connect();
      debug("ContentType is '" + conn.getContentType() + "'");
      debug("ContentEncoding is '" + conn.getContentEncoding() + "'");
      debug("ContentLength is '" + conn.getContentLength() + "'");

      ByteArrayOutputStream ostream = new ByteArrayOutputStream();
      DataInputStream di = new DataInputStream(conn.getInputStream());
      int b = -1;
      while(-1 != (b = di.read()))
         ostream.write(b);
      di.close();
      byte[] data = ostream.toByteArray();
System.out.println("DATA len = " + data.length);
FileOutputStream fos = new FileOutputStream("/home/whunter/tmp/comic.gif");
fos.write(data);
fos.close();
      return data;
   }


   private String getImageSource(String comicUri)
      throws IOException
   {
      String url = _url + comicUri;
      debug("Fetching web source from " + url);
      Document doc = Jsoup.connect(url).get();
      Element picture = doc.select("img.the-comic").first();
//      Element img = picture.getElementsByTag("img").first();
//      return img.attributes().get("src");
      return picture.attributes().get("src");
   }


   private void debug(String msg)
   {
      System.out.println("DEBUG <GoComicsReader>: " + msg);
   }

}
