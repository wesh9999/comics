package org.weshley.comics;

import org.ini4j.Ini;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GoComicsReader
   extends ComicsReader
{
   private static String GO_COMICS_URL = "http://www.gocomics.com/";

   private String _url = GO_COMICS_URL;

   public GoComicsReader()
   {
   }


   public void initializeFromConfig(Ini ini)
   {
      super.initializeFromConfig(ini);
      Ini.Section readerConfig = ini.get("reader");
      _url = readerConfig.get("url");
      if(null == _url)
         _url = GO_COMICS_URL;
      if(!_url.endsWith("/"))
         _url = _url + "/";
   }


   @Override
   public byte[] getImageData(String comicName, Date dt)
      throws ComicsException
   {
      try
      {
         DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
         String comicUri = comicName + "/" + dateFormat.format(dt);
         String src = getImageSource(comicUri);
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
      debug("Reading image data from " + imageSrc);

      URL url = new URL(imageSrc);
      URLConnection conn = url.openConnection();
      debug("ContentType is '" + conn.getContentType() + "'");
// FIXME - hardcoded timeout? retry somehow?
      conn.setReadTimeout(60*1000);  // 60s timeout
      conn.connect();

// FIXME - delete old code
//      byte[] b = new byte[1];
//      String tmpFile = "/home/whunter/tmp/test.gif";
//      File f = new File(tmpFile);
//      if(f.exists())
//         f.delete();
//      debug("Writing image to file " + tmpFile);
      ByteArrayOutputStream ostream = new ByteArrayOutputStream();
      DataInputStream di = new DataInputStream(conn.getInputStream());
//      FileOutputStream fo = new FileOutputStream(tmpFile);
//      while (-1 != di.read(b, 0, 1))
//         fo.write(b, 0, 1);
      int b = -1;
      while(-1 != (b = di.read()))
         ostream.write(b);
      di.close();
//      fo.close();
      return ostream.toByteArray();
   }


   private String getImageSource(String comicUri)
      throws IOException
   {
      String url = _url + comicUri;
      debug("Fetching web source from " + url);
      Document doc = Jsoup.connect(url).get();
      Element picture = doc.select("picture.item-comic-image").first();
      Element img = picture.getElementsByTag("img").first();
      return img.attributes().get("src");
   }


   private void debug(String msg)
   {
      System.out.println("DEBUG <GoComicsReader>: " + msg);
   }

}
