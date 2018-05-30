package org.weshley.comics;

import org.ini4j.Ini;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;


/* TODO:
    - jpgs won't load?  try Bob Gorrell in Arcamax
    - add zoom in/out/reset
    - calendar dropdown -- not supported by all readers (like arcamax)
    - add a message when adding/removing favorites
        - tried adding showGlassMessage, but not working.  partially worked when
          i added it around fetching comic image, but took too long to show.
    - ability to search for a comic in the list
    - do something about hardcoded timeout in the readers when fetching images.
    - what other strip servers are out there to pull from?
         - http://comics.azcentral.com/
               - http://comics.azcentral.com/slideshow?&feature_id=Baby_Blues&feature_date=2018-03-20
         - https://www.washingtonpost.com/entertainment/comics/?utm_term=.61648cc228fa
               - http://wpcomics.washingtonpost.com/client/wpc/ad/2018/03/19/
         - http://comicskingdom.com/comics
               - https://comicskingdom.com/mother-goose-grimm/2018-03-21
    - package comic reader config files with code?  only favorites should be unique per user
    - package jars with a launching script
    - arcamax doesn't support navigation to arbitrary dates, so if you go back a day or
      two with another reader, then navigate to next comic that is from arcamax,
      the reader will fetch today's comic, which is a bit confusing.  don't know a better
      way to deal with this....
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
   private JLabel _glassMessage;

   private ImageIcon _originalImage = null;
   private Calendar _currentDate = Calendar.getInstance();
   private Object _currentComicDate = null;
      // some readers don't support reasonable navigation to
      // arbitrary dates (like arcamax).  this is an generic
      // object provided by the reader that we can use to navigate
      // to previous and next day's comics.  null means "today".
   private ComicsReader _currentReader = null;
   private List<ComicsReader> _readers = new ArrayList<>();
   private Map<String,Comic> _favorites = new HashMap<>();


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
      initializeGlassPane();
      initializeListeners();
      SwingUtilities.invokeLater(new Runnable() { public void run()
      {
         resetUiContent();
         _frame.setSize(new Dimension(900, 600));
      }});
   }


   private void initializeGlassPane()
   {
      JComponent glass = (JComponent) _frame.getGlassPane();
      glass.setLayout(new BorderLayout());
      _glassMessage = new JLabel("test message", JLabel.CENTER);
      _glassMessage.setOpaque(true);
      _glassMessage.setBackground(makeTransparent(Color.white, 220));
      _glassMessage.setFont(_glassMessage.getFont().deriveFont(20.0f));
      glass.add(_glassMessage, BorderLayout.CENTER);
//      glass.setVisible(true);
   }


   private Color makeTransparent(Color c, int alpha)
   {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
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
      Cursor oldCursor = _frame.getCursor();
      _frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
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
            if(null == _currentComicDate)
               _currentDate = Calendar.getInstance();
                  // new reader doesn't support random date navigation, so reset
                  // to today
         }

         try
         {
            byte[] data = c.getImageData(_currentComicDate);
            if(null == data)
               _originalImage = null;
            else
               _originalImage = new ImageIcon(data);
            rescaleImage();
         } catch(ComicsException ex)
         {
            ex.printStackTrace();
            error(ex.getMessage());
         }
      }
      updateUiControlState();
      _frame.setCursor(oldCursor);
   }


   private void clearGlassMessage()
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         public void run()
         {
            _glassMessage.setText("");
            _frame.getGlassPane().setVisible(false);
         }
      });
   }


   private void showGlassMessage(String msg)
   {
      SwingUtilities.invokeLater(new Runnable()
      {
         public void run()
         {
            _glassMessage.setText(msg);
            _frame.getGlassPane().setVisible(true);
            _frame.validate();
         }
      });
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
      return new ImageIcon(image.getImage().getScaledInstance(
         (int) newWidth, (int) newHeight, Image.SCALE_SMOOTH));

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
            || ((null != c) && _favorites.containsKey(c.getId()));
      _addFavoriteButton.setEnabled(!isFavorite);
      _removeFavoriteButton.setEnabled(isFavorite);
   }


   private void addToFavorites()
      throws ComicsException
   {
      Comic c = (Comic) _comicsList.getSelectedValue();
      if(null != c)
      {
         _favorites.put(c.getId(), c);
         writeFavoritesFile();
         _addFavoriteButton.setEnabled(false);
         _removeFavoriteButton.setEnabled(true);
//         showGlassMessage("Added '" + c.getLabel() + "' to favorites");
//         try { Thread.sleep(3000); } catch(InterruptedException ignored) { }
//         clearGlassMessage();
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
         _addFavoriteButton.setEnabled(true);
         _removeFavoriteButton.setEnabled(false);
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
         for(Comic c : _favorites.values())
         {
            String group = c.getReader().getLabel();
            writer.write(c.getId() + " = " + group + "\n");
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
//      DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
//      return dateFormat.format(_currentDate.getTime());
      return getFormattedDate(_currentDate.getTime());
   }

   private String getFormattedDate(Date dt)
   {
      DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
      return dateFormat.format(dt);
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
         for(Comic c : _favorites.values())
            sortedComics.put(c.getLabel(), c);
      }
      else if(group.equalsIgnoreCase("all"))
      {
         for(ComicsReader r : _readers)
         {
            for(Comic c : r.getComics().values())
               sortedComics.put(c.getLabel(), c);
         }
      }
      else
      {
         ComicsReader r = getReaderFromLabel(group);
         for(Comic c : r.getComics().values())
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
      _favorites = new HashMap<String,Comic>();
      Ini.Section favConfig = ini.get("favorites");
      if(null != favConfig)
      {
         for(String id : favConfig.keySet())
         {
            String readerLabel = favConfig.get(id);
            ComicsReader r = getReaderFromLabel(readerLabel);
            if(null == r)
            {
               warn("Could not find reader '" + readerLabel + "' for favorite '" + id + "'");
            }
            else
            {
               Comic c = r.getComic(id);
               if(null == c)
                  warn("Favorite '" + id + "' not found in reader '" + readerLabel + "'");
               else
                  _favorites.put(id, c);
            }
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

      JComponent pane = (JComponent) _frame.getContentPane();
      InputMap inputs = pane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      inputs.put(KeyStroke.getKeyStroke("control N"), "next-comic");
      inputs.put(KeyStroke.getKeyStroke("control P"), "previous-comic");
      inputs.put(KeyStroke.getKeyStroke("control F"), "next-date");
      inputs.put(KeyStroke.getKeyStroke("control B"), "previous-date");
      ActionMap actions = pane.getActionMap();
      actions.put(
         "next-comic",
         new AbstractAction()
         {
            public void actionPerformed(ActionEvent e)
            {
               nextComic();
            }
         });
      actions.put(
         "previous-comic",
         new AbstractAction()
         {
            public void actionPerformed(ActionEvent e)
            {
               previousComic();
            }
         });
      actions.put(
         "next-date",
         new AbstractAction()
         {
            public void actionPerformed(ActionEvent e)
            {
               nextDate();
            }
         });
      actions.put(
         "previous-date",
         new AbstractAction()
         {
            public void actionPerformed(ActionEvent e)
            {
               previousDate();
            }
         });
   }


   private void rescaleImage()
   {
      if(null == _originalImage)
      {
         _comicImage.setIcon(null);
         return;
      }
      Rectangle imageArea = _comicImage.getVisibleRect();
      int w = (int) imageArea.getWidth();
      int h = (int) imageArea.getHeight();
      ImageIcon  image = _originalImage;
      if((w != 0) && (h != 0))
         image = scaleImage(image, w, h);
      _comicImage.setIcon(image);
   }

}
