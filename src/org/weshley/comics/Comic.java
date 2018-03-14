package org.weshley.comics;

import java.util.Date;

public class Comic
{
   private String _id = null;
   private String _label = null;
   private ComicsReader _reader = null;

   public Comic(String id, String label, ComicsReader reader)
   {
      _id = id;
      _label = label;
      _reader = reader;
   }

   public String getId()
   {
      return _id;
   }

   public String getLabel()
   {
      return _label;
   }

   public ComicsReader getReader()
   {
      return _reader;
   }

   public String toString()
   {
      return _label;
   }

   public byte[] getImageData(Date dt)
      throws ComicsException
   {
      return getReader().getImageData(_id, dt);
   }
}
