/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2011 OpenConcerto, by ILM Informatique. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the GNU General Public License Version 3
 * only ("GPL"). You may not use this file except in compliance with the License. You can obtain a
 * copy of the License at http://www.gnu.org/licenses/gpl-3.0.html See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each file.
 */
 
 package org.openconcerto.erp.action;

import static org.openconcerto.task.config.ComptaBasePropsConfiguration.getStreamStatic;
import org.openconcerto.erp.config.ComptaPropsConfiguration;
import org.openconcerto.erp.config.DefaultMenuConfiguration;
import org.openconcerto.erp.config.Gestion;
import org.openconcerto.erp.config.MainFrame;
import org.openconcerto.erp.core.common.ui.PanelFrame;
import org.openconcerto.erp.core.common.ui.StatusPanel;
import org.openconcerto.erp.core.finance.tax.model.TaxeCache;
import org.openconcerto.erp.core.humanresources.payroll.element.CaisseCotisationSQLElement;
import org.openconcerto.erp.element.objet.ClasseCompte;
import org.openconcerto.erp.modules.ModuleFrame;
import org.openconcerto.erp.modules.ModuleManager;
import org.openconcerto.erp.panel.ComptaTipsFrame;
import org.openconcerto.erp.utils.NXDatabaseAccessor;
import org.openconcerto.map.model.Ville;
import org.openconcerto.sql.Configuration;
import org.openconcerto.sql.element.SQLElement;
import org.openconcerto.sql.model.SQLBackgroundTableCache;
import org.openconcerto.sql.model.SQLBase;
import org.openconcerto.sql.model.SQLRow;
import org.openconcerto.sql.model.SQLRowListRSH;
import org.openconcerto.sql.model.SQLRowValues;
import org.openconcerto.sql.model.SQLSelect;
import org.openconcerto.sql.model.SQLTable;
import org.openconcerto.sql.model.UndefinedRowValuesCache;
import org.openconcerto.sql.model.Where;
import org.openconcerto.sql.preferences.SQLPreferences;
import org.openconcerto.sql.preferences.UserProps;
import org.openconcerto.sql.sqlobject.IComboSelectionItem;
import org.openconcerto.sql.ui.ConnexionPanel;
import org.openconcerto.sql.users.User;
import org.openconcerto.sql.users.UserManager;
import org.openconcerto.sql.users.rights.TableAllRights;
import org.openconcerto.sql.users.rights.UserRightsManager;
import org.openconcerto.sql.users.rights.UserRightsManager.RightTuple;
import org.openconcerto.task.config.ComptaBasePropsConfiguration;
import org.openconcerto.ui.DefaultGridBagConstraints;
import org.openconcerto.ui.FrameUtil;
import org.openconcerto.utils.ExceptionHandler;
import org.openconcerto.utils.JImage;
import org.openconcerto.utils.cc.IClosure;
import org.openconcerto.utils.i18n.TranslationManager;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;

import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

public class NouvelleConnexionAction extends CreateFrameAbstractAction {
    private ConnexionPanel connexionPanel;
    // Module configuration frame used when something goes wrong
    private ModuleFrame fMod;

    public NouvelleConnexionAction() {
        super();
        this.putValue(Action.NAME, "Changer d'utilisateur");
    }

    public JFrame createFrame() {
        // needed as done() must come after us
        assert SwingUtilities.isEventDispatchThread();
        final ComptaPropsConfiguration comptaPropsConfiguration = ((ComptaPropsConfiguration) Configuration.getInstance());

        // Vérification de la licence

        Runnable r = new Runnable() {

            public void run() {
                try {
                    TranslationManager.getInstance().addTranslationStreamFromClass(MainFrame.class);
                    TranslationManager.getInstance().setLocale(UserProps.getInstance().getLocale());

                    final Boolean booleanValue = UserProps.getInstance().getBooleanValue("HideTips");
                    if (!booleanValue) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                ComptaTipsFrame.getFrame(true).setVisible(true);
                            }
                        });
                    }
                    int selectedSociete;
                    if (NouvelleConnexionAction.this.connexionPanel != null && !Gestion.isMinimalMode()) {
                        selectedSociete = NouvelleConnexionAction.this.connexionPanel.getSelectedSociete();
                    } else {
                        selectedSociete = UserProps.getInstance().getLastSocieteID();
                        if (selectedSociete < SQLRow.MIN_VALID_ID) {
                            final SQLElement elem = comptaPropsConfiguration.getDirectory().getElement(comptaPropsConfiguration.getRoot().getTable("SOCIETE_COMMON"));
                            final List<IComboSelectionItem> comboItems = elem.getComboRequest().getComboItems();
                            if (comboItems.size() > 0)
                                selectedSociete = comboItems.get(0).getId();
                            else
                                throw new IllegalStateException("No " + elem + " found");
                        }
                    }
                    comptaPropsConfiguration.setUpSocieteDataBaseConnexion(selectedSociete);
                    try {
                        // create table if necessary
                        SQLPreferences.getPrefTable(comptaPropsConfiguration.getRootSociete());
                        SQLPreferences.startMemCached(comptaPropsConfiguration.getRootSociete());
                    } catch (Exception e) {
                        // don't die now, we might not need them
                        ExceptionHandler.handle("Impossible d'accéder aux préférences", e);
                    }
                    // finish filling the configuration before going any further, otherwise the
                    // SQLElementDirectory is not coherent
                    try {
                        ModuleManager.getInstance().setup(comptaPropsConfiguration.getRootSociete(), comptaPropsConfiguration);
                    } catch (Throwable e) {
                        // not OK to continue without required elements
                        openEmergencyModuleManager("Impossible de configurer les modules requis", e);
                    }


                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            // even for quick login, check the license before displaying the main
                            // frame

                            StatusPanel.getInstance().fireStatusChanged();
                            final MainFrame f = new MainFrame();
                            String version = comptaPropsConfiguration.getVersion();
                            final String socTitle = comptaPropsConfiguration.getRowSociete() == null ? "" : ", [Société " + comptaPropsConfiguration.getRowSociete().getString("NOM") + "]";
                            f.setTitle(comptaPropsConfiguration.getAppName() + " " + version + socTitle);
                            f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                            // Init menus
                            DefaultMenuConfiguration mConfiguration = new DefaultMenuConfiguration();
                            mConfiguration.createMenuGroup();
                            mConfiguration.registerMenuActions();
                            f.setJMenuBar(Gestion.isMinimalMode() ? f.createMinimalMenu() : f.createMenu());

                        }
                    });
                    final FutureTask<?> showMainFrame = new FutureTask<Object>(new Runnable() {
                        @Override
                        public void run() {
                            if (ModuleManager.getInstance().isSetup()) {
                                final MainFrame mainFrame = MainFrame.getInstance();
                                FrameUtil.show(mainFrame);
                            }
                        }
                    }, null);
                    ModuleManager.getInstance().invoke(new IClosure<ModuleManager>() {
                        @Override
                        public void executeChecked(ModuleManager input) {
                            // start modules before displaying the frame (e.g. avoid modifying a
                            // visible menu bar)
                            try {
                                input.startRequiredModules();
                                input.startPreviouslyRunningModules();
                                SwingUtilities.invokeLater(showMainFrame);
                            } catch (Exception exn) {
                                openEmergencyModuleManager("Impossible de démarrer les modules requis", exn);
                            }

                        }
                    });
                    initCache();
                    // don't close ConnexionPanel until the main frame is shown
                    showMainFrame.get();
                } catch (Throwable e) {
                    ExceptionHandler.handle("Erreur de connexion", e);
                }
            }

            private void fixEcriture() {
                // FIXME Bug archive ecriture (ecriture non archivé ayant un id_mouvement=1)
                SQLElement elt = Configuration.getInstance().getDirectory().getElement("ECRITURE");
                SQLSelect sel = new SQLSelect();
                sel.addSelect(elt.getTable().getKey());

                Where w = new Where(elt.getTable().getField("ID_MOUVEMENT"), "=", 1);
                sel.setWhere(w);
                System.err.println(sel.asString());
                List<SQLRow> lerrors = (List<SQLRow>) Configuration.getInstance().getBase().getDataSource().execute(sel.asString(), new SQLRowListRSH(elt.getTable()));
                for (SQLRow row : lerrors) {
                    System.err.println("FIX ERROR ID_MOUVEMENT ON ECRITURE ROW " + row.getID());
                    SQLRowValues rowVals = row.createEmptyUpdateRow();
                    rowVals.put("ARCHIVE", 1);
                    try {
                        rowVals.update();
                    } catch (SQLException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }

                if (lerrors.size() > 0) {
                    System.err.println(lerrors.size() + " erreurs");
                    Thread.dumpStack();
                    // JOptionPane.showMessageDialog(null, lerrors.size() +
                    // " erreurs ont été trouvé et corrigé dans la base.");
                }
            }
        };

        final JImage image = new JImage(ComptaBasePropsConfiguration.class.getResource("logo.png"));
        Image customImage = ComptaPropsConfiguration.getInstanceCompta().getCustomLogo();
        if (customImage != null) {
            image.setImage(customImage);
        }
        image.setBackground(Color.WHITE);
        JPanel p = new JPanel();

        p.setLayout(new GridBagLayout());
        GridBagConstraints c = new DefaultGridBagConstraints();
        c.weightx = 1;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 0);
        c.fill = GridBagConstraints.BOTH;

        this.connexionPanel = ConnexionPanel.create(r, image, !Gestion.isMinimalMode());
        if (this.connexionPanel == null)
            return null;
        this.connexionPanel.initLocalization(getClass().getName(),
                Arrays.asList(Locale.FRANCE, Locale.CANADA_FRENCH, new Locale("fr", "CH"), new Locale("fr", "BE"), Locale.UK, Locale.CANADA, Locale.US, Locale.GERMANY, new Locale("de", "CH")));

        p.add(this.connexionPanel, c);
        final PanelFrame panelFrame = new PanelFrame(p, "Connexion");
        panelFrame.setLocationRelativeTo(null);
        panelFrame.pack();
        panelFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        return panelFrame;
    }

    private void initCache() {
        final SQLBase baseSociete = ((ComptaPropsConfiguration) Configuration.getInstance()).getSQLBaseSociete();
        // ensure consistent state before cache loading
        baseSociete.getGraph();
        Thread t = new Thread() {
            @Override
            public void run() {
                // laisse le temps au logiciel de demarrer
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ClasseCompte.loadClasseCompte();
                CaisseCotisationSQLElement.getCaisseCotisation();

                Ville.init(new NXDatabaseAccessor());
                SQLBackgroundTableCache.getInstance().add(baseSociete.getTable("TAXE"), 600);
                SQLBackgroundTableCache.getInstance().add(baseSociete.getTable("PREFS_COMPTE"), 600);
                SQLBackgroundTableCache.getInstance().add(baseSociete.getTable("JOURNAL"), 600);

                SQLBackgroundTableCache.getInstance().add(baseSociete.getTable("COMMERCIAL"), 600);

                SQLBackgroundTableCache.getInstance().add(baseSociete.getTable("TYPE_REGLEMENT"), 1000);
                SQLBackgroundTableCache.getInstance().startCacheWatcher();

                TaxeCache.getCache();

                final UndefinedRowValuesCache undefCache = UndefinedRowValuesCache.getInstance();
                final List<SQLTable> tablesToCache = new ArrayList<SQLTable>();
                tablesToCache.add(baseSociete.getTable("DEVIS"));
                tablesToCache.add(baseSociete.getTable("ETAT_DEVIS"));
                tablesToCache.add(baseSociete.getTable("FAMILLE_ARTICLE"));
                tablesToCache.add(baseSociete.getTable("ADRESSE"));
                tablesToCache.add(baseSociete.getTable("DEVIS_ELEMENT"));
                tablesToCache.add(baseSociete.getTable("CONTACT"));
                tablesToCache.add(baseSociete.getTable("SAISIE_VENTE_FACTURE_ELEMENT"));
                tablesToCache.add(baseSociete.getTable("SAISIE_KM_ELEMENT"));
                tablesToCache.add(baseSociete.getTable("BON_DE_LIVRAISON_ELEMENT"));
                tablesToCache.add(baseSociete.getTable("COMMANDE_CLIENT_ELEMENT"));
                tablesToCache.add(baseSociete.getTable("AVOIR_CLIENT_ELEMENT"));
                tablesToCache.add(baseSociete.getTable("BON_RECEPTION_ELEMENT"));
                undefCache.preload(tablesToCache);
            }

        };
        t.setName("Cache preload");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private String checkLicense(final SwingWorker<String, Object> w) {
        try {
            return w.get();
        } catch (Exception e) {
            throw ExceptionHandler.die("Impossible de charger la licence.", e);
        }
    }

    private void openEmergencyModuleManager(final String str, Throwable e) {
        System.err.println("The following exception is preventing a normal startup:");
        e.printStackTrace();
        System.err.println("Opening the module manager in order to resolve the issue.");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (fMod == null) {
                    fMod = new ModuleFrame();
                    fMod.pack();
                    fMod.setMinimumSize(new Dimension(480, 640));
                    fMod.setLocationRelativeTo(null);
                    fMod.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    fMod.setTitle(str);
                    fMod.setVisible(true);
                }
            }
        });

    }
}
