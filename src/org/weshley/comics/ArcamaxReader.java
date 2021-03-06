package org.weshley.comics;

import org.ini4j.Ini;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;


public class ArcamaxReader
   extends ComicsReader
{
   private static String ARCAMAX_URL = "https://www.arcamax.com";
   private static String ARCAMAX_LIST_URL = ARCAMAX_URL + "/comics";

   private String _url = ARCAMAX_URL;
   private String _nextDate = null;
   private String _previousDate = null;
   private String _imageSrc = null;


   public ArcamaxReader()
   {
   }


   public static void generateIniFile()
      throws ComicsException
   {
      try
      {
         System.out.println("# general reader properties.  class is required");
         System.out.println("[reader]");
         System.out.println("class = org.weshley.comics.ArcamaxReader");
         System.out.println("url = " + ARCAMAX_URL);
         System.out.println("label = ArcaMax");
         System.out.println("");
         System.out.println("# list of comics provided by this reader.  entries are ID = LABEL");
         System.out.println("[comics]");

         Document doc = Jsoup.connect(ARCAMAX_LIST_URL).get();
         Elements links = doc.select("a");
         for(Element e : links)
         {
            String code = e.attributes().get("data-code");
            if((code != null) && !code.isEmpty())
            {
               String label = e.ownText();
               String id = e.attributes().get("href");
               id = id.substring(0, id.length() - 1);
               System.out.println(id + " = " + label);
            }
         }
      }
      catch(Exception ex)
      {
         throw new ComicsException("Error getting all comics from ArcaMax", ex);
      }

   }


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
   public Object nextDate(Object currentDay)
   {
      return _nextDate;
   }


   @Override
   public Object previousDate(Object currentDay)
   {
      return _previousDate;
   }


   @Override
   public Object setDate(Object currentDay)
   {
      // doesn't support random date navigation
      warn("Arcamax doesn't support navigating to arbitrary date!");
      return null;
   }

   @Override
   public boolean hasNextDate(Object currentDay)
   {
      return null != _nextDate;
   }


   @Override
   public boolean hasPreviousDate(Object currentDay)
   {
      return null != _previousDate;
   }


   @Override
   public boolean isToday(Object currentDay)
   {
      return null == currentDay;
   }


   @Override
   public byte[] getImageData(String comicId, Object date)
      throws ComicsException
   {
      try
      {
         String dateId = "";
         if(null != date)
            dateId = "/" + date;
         String comicUri = comicId + dateId;
         parsePage(comicUri);
         return getImageDataInternal(_imageSrc);
      }
      catch(Exception ex)
      {
         throw new ComicsException(ex);
      }
   }

   private byte[] getImageDataInternal(String imageSrc)
      throws IOException
   {
      // at some point around 20 May 2021, imageSrc changed to be the full
      // url of the image....
      URL url = new URL(/*_url + */imageSrc);

      URLConnection conn = url.openConnection();
// FIXME - hardcoded timeout? retry somehow?
      conn.setReadTimeout(60*1000);  // 60s timeout
      conn.connect();

      ByteArrayOutputStream ostream = new ByteArrayOutputStream();
      DataInputStream di = new DataInputStream(conn.getInputStream());
      int b = -1;
      while(-1 != (b = di.read()))
         ostream.write(b);
      di.close();
      return ostream.toByteArray();
   }


   private void parsePage(String comicUri)
      throws IOException
   {
      _imageSrc = null;
      String url = _url + comicUri;
      Document doc = Jsoup.connect(url).get();
      Element elem = doc.select("img.the-comic").first();
      _imageSrc = elem.attributes().get("src");

      // arcamax doesn't seem to have a human readable date format, so
      // pull previous and next date tags from the web page source
      _nextDate = null;
      _previousDate = null;
      Elements elems = doc.select("a.prev");
      if(!elems.isEmpty())
      {
         String href = elems.first().attributes().get("href");
         if(null != href)
         {
            int idx = href.lastIndexOf("/");
            if(-1 != idx)
               _previousDate = href.substring(idx + 1);
         }
      }
      elems = doc.select("a.next");
      if(!elems.isEmpty())
      {
         String href = elems.first().attributes().get("href");
         if(null != href)
         {
            int idx = href.lastIndexOf("/");
            if(-1 != idx)
               _nextDate = href.substring(idx + 1);
         }
      }
   }

}
