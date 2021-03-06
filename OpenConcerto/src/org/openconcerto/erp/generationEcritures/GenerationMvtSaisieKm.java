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
 
 package org.openconcerto.erp.generationEcritures;

import org.openconcerto.erp.core.finance.accounting.element.ComptePCESQLElement;
import org.openconcerto.sql.Configuration;
import org.openconcerto.sql.model.SQLRow;
import org.openconcerto.sql.model.SQLRowValues;
import org.openconcerto.sql.model.SQLTable;
import org.openconcerto.utils.ExceptionHandler;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class GenerationMvtSaisieKm extends GenerationEcritures {

    private int idSaisieKm;
    private static final String source = "SAISIE_KM";

    public GenerationMvtSaisieKm(int idSaisieKm) {

        this.idSaisieKm = idSaisieKm;
    }

    public int genereMouvement() {

        SQLRow saisieRow = base.getTable("SAISIE_KM").getRow(this.idSaisieKm);

        // iniatilisation des valeurs de la map
        this.date = (Date) saisieRow.getObject("DATE");
        this.nom = saisieRow.getObject("NOM").toString();
        this.mEcritures.put("DATE", this.date);
        this.mEcritures.put("NOM", this.nom);
        this.mEcritures.put("ID_JOURNAL", saisieRow.getObject("ID_JOURNAL"));
        this.mEcritures.put("ID_MOUVEMENT", new Integer(1));

        // on calcule le nouveau numero de mouvement
        getNewMouvement(GenerationMvtSaisieKm.source, this.idSaisieKm, 1, "Saisie au km " + saisieRow.getObject("NOM").toString());

        // gnération des ecritures
        SQLTable tableElt = Configuration.getInstance().getRoot().findTable("SAISIE_KM_ELEMENT");
        List<SQLRow> set = saisieRow.getReferentRows(tableElt);

        SQLTable tableAssoc;
        try {
            for (SQLRow rowElement : set) {

                int idCpt = ComptePCESQLElement.getId(rowElement.getString("NUMERO"), rowElement.getString("NOM"));

                // Ajout de l'écriture
                this.mEcritures.put("ID_COMPTE_PCE", new Integer(idCpt));
                this.mEcritures.put("NOM", rowElement.getString("NOM_ECRITURE"));
                this.mEcritures.put("DEBIT", rowElement.getObject("DEBIT"));
                this.mEcritures.put("CREDIT", rowElement.getObject("CREDIT"));
                int idEcr = ajoutEcriture();
                // Mise à jour de la clef étrangère écriture de l'élément saisie au km
                if (idEcr > 1) {
                    SQLRowValues vals = rowElement.createEmptyUpdateRow();
                    vals.put("ID_ECRITURE", new Integer(idEcr));
                    try {
                        vals.update();
                    } catch (NumberFormatException e) {

                        e.printStackTrace();
                    } catch (SQLException e) {

                        e.printStackTrace();
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            ExceptionHandler.handle("Erreur pendant la générations des écritures comptables", e);
            e.printStackTrace();
        }

        return this.idMvt;
    }
}
