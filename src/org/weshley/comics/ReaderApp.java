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
    - keyboard controls to move through comics, days, favorites, etc
    - jpgs won't load?  try Bob Gorrell in Arcamax
    - image magnification is a bit fuzzy.  better method or get higher res images from servers?
    - calendar dropdown -- not supported by all readers (like arcamax)
    - add a message when adding/removing favorites
    - ability to search for a comic in the list
    - "downloading" progress indicator of some sort.  also have a hardcoded timeout in the readers when fetching images.
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
   private JButton _todayButton;
   private JFrame _frame;

   private ImageIcon _originalImage = null;
   private Calendar _currentDate = Calendar.getInstance();
   private Object _currentComicDate = null;
      // some readers don't support reasonable navigation to
      // arbitrary dates (like arcamax).  this is an generic
      // object provided by the reader that we can use to navigate
      // to previous and next day's comics.  null means "today".
   private ComicsReader _currentReader = null;
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
         if(args[0].equals("--find-arcamax-comics"))
            ArcamaxReader.generateIniFile();
         else if(args[0].equals("--test"))
            runTest();
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


   private void runTest()
      throws ComicsException
   {
   }

   private void launchReader()
      throws ComicsException
   {
      loadReaders();
      setUiProperties();
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

      if(0 != _groupCombo.getSelectedIndex())
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
      _frame = new JFrame("Weshley Comics Reader");
      _frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      _frame.setContentPane(_topPanel);
      _frame.pack();
      _frame.setVisible(true);
      initializeListeners();
      resetUiContent();
   }


   private List<String> getComicGroupNames()
   {
      List<String> groups = new ArrayList<String>();
      groups.add("Favorites");
      groups.add("All");

      // alphabetically sort readers by label
      Set<String> sort = new TreeSet<>();
      for(ComicsReader r : _readers)
         sort.add(r.getLabel());
      for(String label : sort)
         groups.add(label);
      return groups;
   }


   private void loadReaders()
      throws ComicsException
   {
      readConfig();
      if(_readers.isEmpty())
         warn("No comic readers configured");
   }


   private void nextComic()
   {
      int current = _comicsList.getSelectedIndex();
      if(current < (_comicsList.getModel().getSize() - 1))
      {
         _comicsList.setSelectedIndex(current + 1);
         _comicsList.ensureIndexIsVisible(current + 1);
      }
   }


   private void previousComic()
   {
      int current = _comicsList.getSelectedIndex();
      if(current > 0)
      {
         _comicsList.setSelectedIndex(current - 1);
         _comicsList.ensureIndexIsVisible(current - 1);
      }
   }


   private void nextDate()
   {
      _currentDate.add(Calendar.DAY_OF_MONTH, 1);
      _currentComicDate = getCurrentReader().nextDate(_currentComicDate);
      updateSelectedComic();
   }


   private void previousDate()
   {
      _currentDate.add(Calendar.DAY_OF_MONTH, -1);
      _currentComicDate = getCurrentReader().previousDate(_currentComicDate);
      updateSelectedComic();
   }


   private void goToToday()
   {
      _currentDate = Calendar.getInstance();
      _currentComicDate =  null;
      updateSelectedComic();
   }


   private ComicsReader getCurrentReader()
   {
      Comic c = (Comic) _comicsList.getSelectedValue();
      if(null == c)
         return null;
      else
         return c.getReader();
   }


   private void updateSelectedComic()
   {
      Comic c = (Comic) _comicsList.getSelectedValue();
      if(null == c)
      {
         _comicImage.setIcon(null);
         _currentComicDate = null;
      }
      else
      {
         // since some readers can't navigate to a random date, might have to
         // reset back to today if we switch readers
         if(_currentReader != c.getReader())
         {
            _currentReader = c.getReader();
            _currentComicDate = _currentReader.setDate(_currentComicDate);
         }

         try
         {
//            _originalImage = new ImageIcon(c.getImageData(_currentDate.getTime()));
            _originalImage = new ImageIcon(c.getImageData(_currentComicDate));
            rescaleImage();
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
      float currentWidth = image.getIconWidth();
      float currentHeight = image.getIconHeight();
      float newWidth = currentWidth;
      float newHeight = currentHeight;
      if(currentWidth > currentHeight) // maximize width
      {
         newWidth = desiredWidth;
         newHeight = (newWidth / currentWidth) * currentHeight;
      }
      else // maximize height
      {
         newHeight = desiredHeight;
         newWidth = (newHeight / currentHeight) * currentWidth;
      }
      return new ImageIcon(image.getImage().getScaledInstance((int) newWidth, (int) newHeight, Image.SCALE_DEFAULT));

   }


   private void updateUiControlState()
   {
      Comic c = (Comic) _comicsList.getSelectedValue();
      if(null == c)
      {
         _comicLabel.setText("");
         _comicImage.setText("No comic selected");
         _previousComicButton.setEnabled(false);
         _nextComicButton.setEnabled(false);
         _addFavoriteButton.setEnabled(false);
         _removeFavoriteButton.setEnabled(false);
      }
      else
      {
         _comicLabel.setText(c.getLabel());
         _comicImage.setText("");
         _previousComicButton.setEnabled(_comicsList.getSelectedIndex() > 0);
         _nextComicButton.setEnabled(
            _comicsList.getSelectedIndex() < (_comicsList.getModel().getSize() - 1));
      }
      _previousDayButton.setEnabled(false);
      _nextDayButton.setEnabled(false);
      _todayButton.setEnabled(false);
      ComicsReader r = getCurrentReader();
      if(null != r)
      {
         _nextDayButton.setEnabled(r.hasNextDate(_currentComicDate));
         _previousDayButton.setEnabled(r.hasPreviousDate(_currentComicDate));
         _todayButton.setEnabled(!r.isToday(_currentComicDate));
      }
      _dateLabel.setText(getCurrentDayFormatted());

      String group = (String) _groupCombo.getSelectedItem();
      boolean isFavorite =
         ((null != group) && group.equals("Favorites"))
            || ((null != c) && _favorites.contains(c.getId()));
      _addFavoriteButton.setEnabled(!isFavorite);
      _removeFavoriteButton.setEnabled(isFavorite);
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
         for(String id : _favorites)
         {
            String group = findComicGroup(id);
            writer.write(id + " = " + group + "\n");
         }
         writer.write("\n");
         writer.flush();
         writer.close();
      }
      catch(Exception ex)
      {
         throw new ComicsException("Error writing ini file: " + ex.getMessage(), ex);
      }
   }


   private String findComicGroup(String id)
   {
      for(ComicsReader r : _readers)
      {
         if(r.hasComic(id))
            return r.getLabel();
      }
      return "Unknown";
   }

   private String getCurrentDayFormatted()
   {
      DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
      return dateFormat.format(_currentDate.getTime());
   }


   private void updateComicsList()
   {
      String group = (String) _groupCombo.getSelectedItem();
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

      if(0 != _comicsList.getSelectedIndex())
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
      _todayButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            goToToday();
         }
      });

      _previousDayButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            previousDate();
         }
      });

      _nextDayButton.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            nextDate();
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

      _frame.addComponentListener(new ComponentAdapter()
      {
         @Override
         public void componentResized(ComponentEvent e)
         {
            rescaleImage();
         }
      });
   }


   private void rescaleImage()
   {
      if(null == _originalImage)
         return;
      Rectangle imageArea = _comicImage.getVisibleRect();
      int w = (int) imageArea.getWidth();
      int h = (int) imageArea.getHeight();
      ImageIcon  image = _originalImage;
      if((w != 0) && (h != 0))
         image = scaleImage(image, w, h);
      _comicImage.setIcon(image);
   }

}
