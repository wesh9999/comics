package org.weshley.comics;

public class ComicsException
   extends Exception
{
   public ComicsException(Throwable cause)
   {
      super(cause);
   }

   public ComicsException(String msg, Throwable cause)
   {
      super(msg, cause);
   }

   public ComicsException(String msg)
   {
      super(msg);
   }
}
