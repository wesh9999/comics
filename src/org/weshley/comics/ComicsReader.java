package org.weshley.comics;

import org.ini4j.Ini;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public abstract class ComicsReader
{
   protected String _label = null;

   protected Map<String,Comic> _comics = new HashMap<String,Comic>();
      // keyed by unique comic ID


   public void initializeFromConfig(Ini ini)
   {
      Ini.Section readerConfig = ini.get("reader");
      _label = readerConfig.get("label");
      if((null == _label) || _label.isEmpty())
         _label = readerConfig.get("class");

      _comics = new HashMap<String,Comic>();
      Ini.Section comics = ini.get("comics");
      if(null != comics)
      {
         for(String id : comics.keySet())
            _comics.put(id, new Comic(id, comics.get(id), this));
      }
      if(_comics.isEmpty())
         warn("No comics defined for reader '" + _label + "'");
   }


   public String getLabel()
   {
      return _label;
   }


   public Map<String,Comic> getComics()
   {
      return _comics;
   }


   public byte[] getImageData(String comicName)
      throws ComicsException
   {
      return getImageData(comicName, new Date());
   }


   public abstract byte[] getImageData(String comicName, Date dt)
      throws ComicsException;


   protected void warn(String msg)
   {
      System.out.println("WARNING <" + _label + ">: " + msg);
   }


   protected void debug(String msg)
   {
      System.out.println("DEBUG <" + _label + ">: " + msg);
   }


   // for testing
   protected void writeImageFile(String filePath, byte[] data)
      throws IOException
   {
      FileOutputStream fos = new FileOutputStream(filePath);
      fos.write(data);
      fos.close();
   }
}
