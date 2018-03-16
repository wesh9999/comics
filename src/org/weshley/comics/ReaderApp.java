package org.weshley.comics;

import org.ini4j.Ini;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;


/* TODO:
    - fetching image twice on startup?
    - calendar dropdown
    - add a message when adding/removing favorites
    - add/remove favorite button not initially visible when unless the entire comic is visible.
      probably fixed if i can figure out how to scale the image?
    - ability to search for a comic in the list
    - scale image to fit into available space
         - resize listener to rescale when window size changes
            - don't get events when making window smaller, only larger.  something odd about how the control is configured?
    - add to and remove from favorites button
       - write favorites to $HOME/.wcomics/comics.ini when changed in UI
    - "downloading" progress indicator of some sort
    - test support for image types other than gif
    - what other strip servers are out there to pull from?
    - package comic reader config files with code?  only favorites should be unique per user
    - package jars with a launching script
 */

public class ReaderApp
{
   private JPanel _topPanel;
   private JPanel _leftPanel;
   private JComboBox _groupCombo;
   private JScrollPane _comicsScroller;
   private JList _comicsList;
   private JPanel _rightTopPanel;
   private JLabel _comicLabel;
   private JButton _previousDayButton;
   private JLabel _dateLabel;
   private JButton _nextDayButton;
   private JLabel _comicImage;
   private JButton _previousComicButton;
   private JButton _nextComicButton;
   private JPanel _comicsButtonPanel;
   private JButton _addFavoriteButton;
   private JButton _removeFavoriteButton;

   private ImageIcon _originalImage = null;
   private Calendar _currentDate = Calendar.getInstance();
   private List<ComicsReader> _readers = new ArrayList<>();
   private Set<String> _favorites = new HashSet<>();
      // stores comic ids
   private Map<String,Comic> _allComics = new HashMap<>();
      // maps comic ids to comic objects


   public static void main(String[] args)
   {
      try
      {
         (new ReaderApp()).run(args);
      }
      catch(Throwable t)
      {
         t.printStackTrace();
      }
   }


   private ReaderApp()
   {
   }


   private void usage()
   {
      System.out.println("usage: java org.weshley.comics.ReaderApp {--find-go-comics}");
   }


   private void run(String[] args)
      throws ComicsException
   {
      if(1 == args.length)
      {
         if(args[0].equals("--find-go-comics"))
            GoComicsReader.generateIniFile();
         else if(args[0].equals("--help"))
            usage();
         else
            throw new ComicsException("Illegal argument '" + args[0]);
      }
      else if(0 == args.length)
         launchReader();
      else
         throw new ComicsException("Illegal argument '" + args[0]);
   }


   private void launchReader()
      throws ComicsException
   {
      loadReaders();
      setUiProperties();
      initializeListeners();
      resetUiContent();
      launchUi();
   }


   private void setUiProperties()
   {
      _comicsList.setModel(new DefaultListModel());
   }


   private void resetUiContent()
   {
      List<String> names = getComicGroupNames();
      for(String s : names)
         _groupCombo.addItem(s);

      _groupCombo.setSelectedIndex(0);
   }

   private void launchUi()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         public void run()
         {
            buildUi();
         }
      });
   }


   private void buildUi()
   {
      JFrame frame = new JFrame("Weshley Comics Reader");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setContentPane(_topPanel);
      frame.pack();
      frame.setVisible(true);
   }


   private List<String> getComicGroupNames()
   {
      List<String> groups = new ArrayList<String>();
      groups.add("Favorites");
      groups.add("All");
      for(ComicsReader r : _readers)
         groups.add(r.getLabel());
      return groups;
   }


   private void loadReaders()
      throws ComicsException
   {
      readConfig();
      if(_readers.isEmpty())
         warn("No comic readers configured");

/*
// FIXME - remove debugging code

      for(ComicsReader reader : _readers)
      {
         System.out.println("------------ " + reader.getLabel() + " ------------");
         Map<String,Comic> comics = reader.getComics();
         for(Comic c : comics.values())
            System.out.println("   " + c.getLabel() + " (" + c.getId() + ") from '" + c.getReader().getLabel() + "'");
      }
 */
   }

   private void nextComic()
   {
      int current = _comicsList.getSelectedIndex();
      if(current < (_comicsList.getModel().getSize() - 1))
         _comicsList.setSelectedIndex(current + 1);
   }


   private void previousComic()
   {
      int current = _comicsList.getSelectedIndex();
      if(current > 0)
         _comicsList.setSelectedIndex(current - 1);
   }


   private void nextDay()
   {
      _currentDate.add(Calendar.DAY_OF_MONTH, 1);
      updateSelectedComic();
   }


   private void previousDay()
   {
      _currentDate.add(Calendar.DAY_OF_MONTH, -1);
      updateSelectedComic();
   }


   private void updateSelectedComic()
   {
      Comic c = (Comic) _comicsList.getSelectedValue();
//      debug("COMIC: " + c);
      if(null == c)
         _comicImage.setIcon(null);
      else
      {
         try
         {
            _originalImage = new ImageIcon(c.getImageData(_currentDate.getTime()));
//            _comicImage.setIcon(_originalImage);
            rescaleImage();
/*
//            debug("LOADED IMAGE SIZE=" + image.getIconWidth() + "," + image.getIconHeight());
//            debug("LABEL SIZE=" + _comicImage.getWidth() + "," + _comicImage.getHeight());
            Rectangle imageArea = _comicImage.getVisibleRect();
//            debug("LABEL SIZE=" + imageArea);
            int w = (int) imageArea.getWidth();
            int h = (int) imageArea.getHeight();
            if((w != 0) && (h != 0))
               image = scaleImage(image, w, h);
            _comicImage.setIcon(image);
*/
         }
         catch(ComicsException ex)
         {
            ex.printStackTrace();
            error(ex.getMessage());
         }
      }
      updateUiControlState();
   }

   // scale an image to a desired size, maintaining the original image's aspect ratio
   private ImageIcon scaleImage(ImageIcon image, int desiredWidth, int desiredHeight)
   {
/*
      int newWidth = image.getIconWidth();
      int newHeight = image.getIconHeight();

      if(image.getIconWidth() > desiredWidth)
      {
         newWidth = desiredWidth;
         newHeight = (newWidth * image.getIconHeight()) / image.getIconWidth();
      }

      if(newHeight > desiredHeight)
      {
         newHeight = desiredHeight;
         newWidth = (image.getIconWidth() * newHeight) / image.getIconHeight();
      }
System.out.println("NEW SIZE=" + newWidth + ", " + newHeight);
      return new ImageIcon(image.getImage().getScaledInstance(newWidth, newHeight, Image.SCALE_DEFAULT));
*/
      int currentWidth = image.getIconWidth();
      int currentHeight = image.getIconHeight();
      int newWidth = currentWidth;
      int newHeight = currentHeight;
      if(currentWidth > currentHeight) // scale width
      {
         newWidth = desiredWidth;
         newHeight = (newWidth * currentHeight) / currentWidth;
      }
      else // scale height
      {
         newHeight = desiredHeight;
         newWidth = (newHeight * currentWidth) / currentHeight;
      }
      System.out.println("NEW SIZE=" + newWidth + ", " + newHeight);
      return new ImageIcon(image.getImage().getScaledInstance(newWidth, newHeight, Image.SCALE_DEFAULT));
   }


   private void updateUiControlState()
   {
      Comic c = (Comic) _comicsList.getSelectedValue();
      if(null == c)
      {
         _comicLabel.setText("");
         _comicImage.setText("No comic selected");
         _previousDayButton.setEnabled(false);
         _nextDayButton.setEnabled(false);
         _previousComicButton.setEnabled(false);
         _nextComicButton.setEnabled(false);
         _addFavoriteButton.setEnabled(false);
         _removeFavoriteButton.setEnabled(false);
      }
      else
      {
         _comicLabel.setText(c.getLabel());
         _comicImage.setText("");
         Calendar today = Calendar.getInstance();
         _previousDayButton.setEnabled(true);
         _nextDayButton.setEnabled(compareDate(_currentDate, today) < 0);
         _previousComicButton.setEnabled(_comicsList.getSelectedIndex() > 0);
         _nextComicButton.setEnabled(
            _comicsList.getSelectedIndex() < (_comicsList.getModel().getSize() - 1));
      }
      _dateLabel.setText(getCurrentDayFormatted());
      _addFavoriteButton.setEnabled(true);
      _removeFavoriteButton.setEnabled(true);
   }


   private void addToFavorites()
      throws ComicsException
   {
      Comic c = (Comic) _comicsList.getSelectedValue();
      if(null != c)
      {
         _favorites.add(c.getId());
         writeFavoritesFile();
      }
   }


   private void removeFromFavorites()
      throws ComicsException
   {
      Comic c = (Comic) _comicsList.getSelectedValue();
      if(null != c)
      {
         _favorites.remove(c.getId());
         writeFavoritesFile();
      }
   }


   private void writeFavoritesFile()
      throws ComicsException
   {
      try
      {
         File iniFile = new File(getConfigDir(), "comics.ini");
         if(iniFile.exists())
            iniFile.delete();
         FileWriter writer = new FileWriter(iniFile);
         writer.write("[favorites]\n");
         String group = _groupCombo.getSelectedItem().toString();
         for(String id : _favorites)
            writer.write(id + " = " + group + "\n");
         writer.write("\n");
         writer.flush();
         writer.close();
      }
      catch(Exception ex)
      {
         throw new ComicsException("Error writing ini file: " + ex.getMessage(), ex);
      }

   }


   private String getCurrentDayFormatted()
   {
      DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
      return dateFormat.format(_currentDate.getTime());
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


   private void updateComicsList()
   {
      String group = (String) _groupCombo.getSelectedItem();
//      debug("GROUP: " + group);
      DefaultListModel model = (DefaultListModel) _comicsList.getModel();
      model.clear();
      if(null == group)
         return;

      // use map to alphabetically sort comics by label
      Map<String,Comic> sortedComics = new TreeMap<>();
      if(group.equalsIgnoreCase("favorites"))
      {
         for(String id : _favorites)
         {
            Comic c = _allComics.get(id);
            sortedComics.put(c.getLabel(), c);
         }
      }
      else if(group.equalsIgnoreCase("all"))
      {
         for(Comic c : _allComics.values())
            sortedComics.put(c.getLabel(), c);
      }
      else
      {
         ComicsReader r = getReaderFromLabel(group);
         for(Comic c : _allComics.values())
         {
            if(c.getReader() == r)
               sortedComics.put(c.getLabel(), c);
         }
      }
      for(Map.Entry<String,Comic> e : sortedComics.entrySet())
         model.addElement(e.getValue());

      _comicsList.setSelectedIndex(0);
   }


   private ComicsReader getReaderFromLabel(String label)
   {
      for(ComicsReader r: _readers)
      {
         if(r.getLabel().equals(label))
            return r;
      }
      return null;
   }


   private File getConfigDir()
   {
      String homeDir = System.getProperty("user.home");
      return new File(homeDir, ".wcomics");
   }


   private void readConfig()
      throws ComicsException
   {
      // $HOME/.wcomics/ has a directory for each comic source (i.e. gocomics).
      // Each comic source directory has a reader.ini file the specifies
      // the the class that implements the reader, list of comics available from
      // the source, etc.  $HOME/.wcomics/ also has a comics.ini file that has
      // list of favorites (maybe other stuff later).
      //
      // # ini file example
      // [reader]
      // class = org.weshley.comics.GoComicsReader
      // url = "http://www.gocomics.com"
      // label = "Go Comics"
      //
      // [comics]
      // bc = BC
      // adamathome = Adam@Home
      // forbetterorforworse = For Better or For Worse
      //

      File configDir = getConfigDir();
      debug("Loading config from '" + configDir.getAbsolutePath() + "'");
      if(configDir.exists() && !configDir.isDirectory())
      {
         throw new ComicsException("Cannot overwrite regular file '"
            + configDir.getAbsolutePath() + "' with config directory");
      }
      if(!configDir.exists())
         configDir.mkdirs();

      for(File comicDir : configDir.listFiles())
      {
         if(comicDir.isDirectory())
            initializeReader(comicDir);
      }

      readFavorites();
   }


   private File getIniFile()
   {
      return new File(getConfigDir(), "comics.ini");
   }


   private void readFavorites()
      throws ComicsException
   {
      File iniFile = getIniFile();
      if(!iniFile.exists() || iniFile.isDirectory())
      {
         warn("Missing comics.ini");
         return;
      }

      Ini ini = new Ini();
      try
      {
         ini.load(new FileReader(iniFile));
      }
      catch(IOException ex)
      {
         throw new ComicsException("Error reading ini file '" + iniFile.getAbsolutePath() + "'", ex);
      }
      _favorites = new HashSet<String>();
      Ini.Section favConfig = ini.get("favorites");
      if(null != favConfig)
      {
         for(String id : favConfig.keySet())
         {
            if(_allComics.containsKey(id))
               _favorites.add(id);
            else
               warn("Favorite '" + id + "' not registered with any reader");
         }
      }
   }

   private void initializeReader(File comicDir)
      throws ComicsException
   {
      File iniFile = new File(comicDir, "reader.ini");
      if(!iniFile.exists())
      {
         warn("Missing reader.ini in '" + comicDir.getAbsolutePath() + "'");
         return;
      }

      Ini ini = new Ini();
      try
      {
         ini.load(new FileReader(iniFile));
      }
      catch(IOException ex)
      {
         throw new ComicsException("Error reading ini file '" + comicDir.getAbsolutePath() + "'", ex);
      }
      Ini.Section readerConfig = ini.get("reader");
      String readerClass = readerConfig.get("class");
      ComicsReader reader = instantiateReader(readerClass);
      reader.initializeFromConfig(ini);
      _readers.add(reader);
      _allComics.putAll(reader.getComics());
   }


   private ComicsReader instantiateReader(String className)
      throws ComicsException
   {
      try
      {
         Class cls = Class.forName(className);
         return (ComicsReader) cls.newInstance();
      }
      catch(ClassNotFoundException | IllegalAccessException | InstantiationException ex)
      {
         throw new ComicsException("Error creating reader class '" + className + "'", ex);
      }
   }


   private void error(String msg)
   {
      System.out.println("ERROR: " + msg);
   }


   private void warn(String msg)
   {
      System.out.println("WARNING: " + msg);
   }


   private void debug(String msg)
   {
      System.out.println("DEBUG<Main>: " + msg);
   }


   private void initializeListeners()
   {
      _previousDayButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            previousDay();
         }
      });

      _nextDayButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            nextDay();
         }
      });

      _previousComicButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            previousComic();
         }
      });

      _nextComicButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            nextComic();
         }
      });

      _addFavoriteButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            try
            {
               addToFavorites();
            }
            catch(ComicsException ex)
            {
               error(ex.getMessage());
               ex.printStackTrace();
            }
         }
      });
      _removeFavoriteButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            try
            {
               removeFromFavorites();
            }
            catch(ComicsException ex)
            {
               error(ex.getMessage());
               ex.printStackTrace();
            }
         }
      });

      _groupCombo.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            updateComicsList();
         }
      });

      _comicsList.addListSelectionListener(new ListSelectionListener()
      {
         @Override
         public void valueChanged(ListSelectionEvent e)
         {
            if(!e.getValueIsAdjusting())
               updateSelectedComic();
         }
      });


      _comicImage.addComponentListener(new ComponentAdapter()
      {
         @Override
         public void componentResized(ComponentEvent e)
         {
            System.out.println("resizing");
            rescaleImage();
         }
      });
   }


   private void rescaleImage()
   {
//      ImageIcon image = (ImageIcon) _comicImage.getIcon();
      if(null == _originalImage)
         return;
//            debug("LOADED IMAGE SIZE=" + image.getIconWidth() + "," + image.getIconHeight());
//      debug("LABEL SIZE=" + _comicImage.getWidth() + "," + _comicImage.getHeight());
      Rectangle imageArea = _comicImage.getVisibleRect();
      debug("LABEL SIZE=" + imageArea);
      int w = (int) imageArea.getWidth();
      int h = (int) imageArea.getHeight();
      ImageIcon  image = _originalImage;
      if((w != 0) && (h != 0))
         image = scaleImage(image, w, h);
      _comicImage.setIcon(image);
//      _topPanel.revalidate();
   }

}
