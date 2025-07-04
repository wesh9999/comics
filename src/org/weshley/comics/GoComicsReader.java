package org.weshley.comics;

import org.ini4j.Ini;
import org.json.*;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;


public class GoComicsReader
   extends ComicsReader
{
   private static String GO_COMICS_URL = "http://www.gocomics.com";
   private static String GO_COMICS_LIST_URL = GO_COMICS_URL + "/comics/a-to-z";

   private String _url = GO_COMICS_URL;


   public GoComicsReader()
   {
   }


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


   public void initializeFromConfig(Ini ini)
   {
      super.initializeFromConfig(ini);
      Ini.Section readerConfig = ini.get("reader");
      _url = readerConfig.get("url");
      if(null == _url)
         _url = GO_COMICS_URL;
      if(_url.endsWith("/"))
         _url = _url.substring(0, _url.length() - 1);
   }


   @Override
   public Object nextDate(Object currentDay)
   {
      Calendar cur = Calendar.getInstance();
      if((null != currentDay) && (currentDay instanceof Calendar))
         cur = (Calendar) currentDay;
      cur.add(Calendar.DAY_OF_MONTH, 1);
      return cur;
   }


   @Override
   public Object previousDate(Object currentDay)
   {
      Calendar cur = Calendar.getInstance();
      if((null != currentDay) && (currentDay instanceof Calendar))
         cur = (Calendar) currentDay;
      cur.add(Calendar.DAY_OF_MONTH, -1);
      return cur;
   }


   @Override
   public Object setDate(Object currentDay)
   {
      if((null != currentDay) && (currentDay instanceof Calendar))
         return currentDay;
      return Calendar.getInstance();
   }


   @Override
   public boolean hasNextDate(Object currentDay)
   {
      if((null == currentDay) || !(currentDay instanceof Calendar))
         return false; // assuming this means "today"
      Calendar cur = (Calendar) currentDay;
      Calendar today = Calendar.getInstance();
      int c = compareDate(cur, today);
      return (c < 0);
   }


   @Override
   public boolean hasPreviousDate(Object currentDay)
   {
      if((null == currentDay) || !(currentDay instanceof Calendar))
         return true; // assuming this means "today"
      Calendar cur = (Calendar) currentDay;
      Calendar today = Calendar.getInstance();
      int c = compareDate(cur, today);
      return (c <= 0);
   }


   @Override
   public boolean isToday(Object currentDay)
   {
      if((null == currentDay) || !(currentDay instanceof Calendar))
         return true; // assuming this means "today"
      Calendar cur = (Calendar) currentDay;
      Calendar today = Calendar.getInstance();
      int c = compareDate(cur, today);
      return (c == 0);
   }


   @Override
   public byte[] getImageData(String comicName, Object date)
      throws ComicsException
   {
      try
      {
         Calendar cal = Calendar.getInstance();
         if((null != date) && (date instanceof Calendar))
            cal = (Calendar) date;
         Date dt = cal.getTime();
         DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
         String comicUri = comicName + "/" + dateFormat.format(dt);
         String src = null;
         try
         {
            src = getImageSource(comicUri);
         }
         catch(HttpStatusException hex)
         {
            // if we get this, try without the date.  probably don't have this comic on this day so just so the last one
            src = getImageSource(comicName);
         }
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
      if((null == imageSrc) || imageSrc.isEmpty())
         return null;

      URL url = new URL(imageSrc);
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

   private String getImageSource(String comicUri)
      throws IOException
   {
      String url = _url + "/" + comicUri;
      Document doc = Jsoup.connect(url).get();
//      String pictureUrl = getPictureUrlApril2025(doc);
//      String pictureUrl = getPictureUrlMay2025(doc);
      String pictureUrl = getPictureUrlJune2025(doc);
      if(null == pictureUrl)
      {
         System.err.println("ERROR:  Could not find image in document");
//         System.out.println(doc.toString());
         return null;
      }
      return pictureUrl;
   }

   private String getPictureUrlJune2025(Document doc)
   {
      Elements metas = doc.select("meta");
      if((null == metas) || metas.isEmpty())
         return null;
      for(Element e : metas)
      {
         Attributes attrSet = e.attributes();
         String prop = attrSet.get("property");
         if((null != prop) && prop.equals("og:image"))
         {
            String content = attrSet.get("content");
            if(null != content)
               return content;
         }
      }
      return null;
   }

   private String getPictureUrlMay2025(Document doc)
   {
      Elements divs = doc.select("div");
      if((null == divs) || divs.isEmpty())
         return null;
      for(Element e : divs)
      {
         Attributes attrSet = e.attributes();
         String val = attrSet.get("class");
         if((null != val) && val.startsWith("ShowComicViewer_showComicViewer__comic"))
         {
            if(e.childNodeSize() == 1)
            {
               Elements scripts = e.select("script");
               if(scripts.size() == 1)
               {
                  if(scripts.get(0).dataNodes().size() == 1)
                  {
                     String json = scripts.get(0).dataNodes().get(0).outerHtml();
                     JSONObject jObj = new JSONObject(json);
                     String url = jObj.getString("contentUrl");
                     return url;
                  }
               }
            }
         }
      }
      return null;
   }

   private String getPictureUrlApril2025(Document doc)
   {
      Elements links = doc.select("link");
      if((null == links) || links.isEmpty())
      {
         //System.err.println("ERROR:  Could not links in document head");
         return null;
      }
      for(Element e : links)
      {
         Attributes attrSet = e.attributes();
         String val = attrSet.get("imageSrcSet");
         if(null != val)
         {
            int idx = val.indexOf("?");
            if(-1 != idx)
            {
               String pictureUrl = val.substring(0, idx);
               return pictureUrl;
            }
         }
      }

      //System.err.println("ERROR:  Could not find link with imageSrcSet attribute");
      return null;
   }


   // return -1 if c1 is a day before c2, 1 if c1 is a day after c2,
   // and 0 if days are the same.  ignore time
   private int compareDate(Calendar c1, Calendar c2)
   {
      int y1 = c1.get(Calendar.YEAR);
      int y2 = c2.get(Calendar.YEAR);
      int m1 = c1.get(Calendar.MONTH);
      int m2 = c2.get(Calendar.MONTH);
      int d1 = c1.get(Calendar.DAY_OF_MONTH);
      int d2 = c2.get(Calendar.DAY_OF_MONTH);

      if(y1 < y2)
         return -1;
      else if(y1 > y2)
         return 1;

      // same year
      if(m1 < m2)
         return -1;
      else if(m1 > m2)
         return 1;

      // same month
      if(d1 < d2)
         return -1;
      else if(d1 > d2)
         return 1;

      return 0;
   }


}
