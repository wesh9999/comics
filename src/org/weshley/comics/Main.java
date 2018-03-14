package org.weshley.comics;

import org.ini4j.Ini;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/* TODO:
    - create github project
    - calendar dropdown
    - fix layout and resizing
       - group combo fixed
       - scrolling comic list with fixed with
       - title in a larger font on left side above comic
       - date with next/prev buttons on right side above comic
       - resizing gives comic more space always
           - scale image to fit into available space
       - next/prev comics buttons centered below image?
    - add to and remove from favorites button
       - write favorites to $HOME/.wcomics/comics.ini when changed in UI
    - "downloading" progress indicator of some sort
    - test support for image types other than gif
    - test with all GoComics strips
    - what other strip servers are out there to pull from?
    - package comic reader config files with code?  only favorites should be unique per user
 */
public class Main
   implements ActionListener, ListSelectionListener
{
   private Calendar _currentDate = Calendar.getInstance();
   private List<ComicsReader> _readers = new ArrayList<>();
   private Set<String> _favorites = new HashSet<>();
      // stores comic ids
   private Map<String,Comic> _allComics = new HashMap<>();
      // maps comic ids to comic objects

   private JComboBox _groupCombo = null;
   private JList _comicsList = null;
   private JLabel _comicLabel = null;
   private JLabel _dateLabel = null;
   private JButton _previousDayButton = null;
   private JButton _nextDayButton = null;
   private JButton _previousComicButton = null;
   private JButton _nextComicButton = null;
   private JLabel _comicImage = null;


   public static void main(String[] args)
   {
      try
      {
         (new Main()).run();
      }
      catch(Throwable t)
      {
         t.printStackTrace();
      }
   }


   private Main()
      throws ComicsException
   {
      readConfig();
      if(_readers.isEmpty())
         warn("No comic readers configured");

// FIXME - remove debugging code
for(ComicsReader reader : _readers)
{
   System.out.println("------------ " + reader.getLabel() + " ------------");
   Map<String,Comic> comics = reader.getComics();
   for(Comic c : comics.values())
      System.out.println("   " + c.getLabel() + " (" + c.getId() + ") from '" + c.getReader().getLabel() + "'");
}
   }


   public void actionPerformed(ActionEvent e)
   {
      if(e.getSource() == _groupCombo)
         updateComicsList();
      else if(e.getSource() == _previousComicButton)
         previousComic();
      else if(e.getSource() == _nextComicButton)
         nextComic();
      else if(e.getSource() == _previousDayButton)
         previousDay();
      else if(e.getSource() == _nextDayButton)
         nextDay();
   }


   public void valueChanged(ListSelectionEvent e)
   {
      if(e.getSource() == _comicsList)
      {
         if(!e.getValueIsAdjusting())
            updateSelectedComic();
      }
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
      debug("COMIC: " + c);
      if(null == c)
         _comicImage.setIcon(null);
      else
      {
         try
         {
            _comicImage.setIcon(new ImageIcon(c.getImageData(_currentDate.getTime())));
         }
         catch(ComicsException ex)
         {
            ex.printStackTrace();
            error(ex.getMessage());
         }
      }
      updateUiControlState();
      // FIXME - fetch and display image
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
      debug("GROUP: " + group);
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

      String homeDir = System.getProperty("user.home");
      File configDir = new File(homeDir, ".wcomics");
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

      readFavorites(configDir);
   }


   private void readFavorites(File configDir)
      throws ComicsException
   {
      File iniFile = new File(configDir, "comics.ini");
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
         throw new ComicsException("Error reading ini file '" + configDir.getAbsolutePath() + "'", ex);
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


   private void run()
      throws ComicsException
   {
      // testGoComics();
      launchUi();
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

      JPanel outerPanel = new JPanel();
      outerPanel.setLayout(new BoxLayout(outerPanel, BoxLayout.LINE_AXIS));
      outerPanel.add(buildLeftPanel());
      outerPanel.add(buildRightPanel());
      frame.getContentPane().add(outerPanel);

      frame.pack();
      frame.setVisible(true);

      _groupCombo.setSelectedIndex(0);
   }


   private JPanel buildRightPanel()
   {
      JPanel rightPanel = new JPanel();
      rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.PAGE_AXIS));

      // top panel contains comic name, date, and next/previous day buttons
      JPanel topPanel = new JPanel();
      topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));
      _comicLabel = new JLabel("");
      topPanel.add(_comicLabel);
      _previousDayButton = new JButton("<<");
      _previousDayButton.addActionListener(this);
      topPanel.add(_previousDayButton);
      _dateLabel = new JLabel(getCurrentDayFormatted());
      topPanel.add(_dateLabel);
      _nextDayButton = new JButton(">>");
      _nextDayButton.addActionListener(this);
      topPanel.add(_nextDayButton);
      rightPanel.add(topPanel);

      _comicImage = new JLabel("No comic selected");
      _comicImage.setPreferredSize(new Dimension(800, 300));
      rightPanel.add(_comicImage);

      // bottom panel has next/previous comic buttons
      JPanel bottomPanel = new JPanel();
      bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.LINE_AXIS));
      _previousComicButton = new JButton("Previous");
      _previousComicButton.addActionListener(this);
      bottomPanel.add(_previousComicButton);
      _nextComicButton = new JButton("Next");
      _nextComicButton.addActionListener(this);
      bottomPanel.add(_nextComicButton);
      rightPanel.add(bottomPanel);

      return rightPanel;
   }


   private String getCurrentDayFormatted()
   {
      DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
      return dateFormat.format(_currentDate.getTime());
   }


   private JPanel buildLeftPanel()
   {
      JPanel leftPanel = new JPanel();
      leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.PAGE_AXIS));
      leftPanel.add(buildComicGroupCombo());
      leftPanel.add(buildComicsList());
      return leftPanel;
   }

   private JComboBox buildComicGroupCombo()
   {
      _groupCombo = new JComboBox();
      List<String> names = getComicGroupNames();
      for(String s : names)
         _groupCombo.addItem(s);
      _groupCombo.addActionListener(this);
      return _groupCombo;
   }


   private JComponent buildComicsList()
   {
      _comicsList = new JList();
      _comicsList.setModel(new DefaultListModel());
      _comicsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
      _comicsList.addListSelectionListener(this);
      JScrollPane scroller = new JScrollPane(_comicsList);
      scroller.setPreferredSize(new Dimension(250, 80));
      return scroller;
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

}
