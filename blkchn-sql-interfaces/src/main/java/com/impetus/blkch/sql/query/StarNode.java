/*******************************************************************************
 * * Copyright 2017 Impetus Infotech.
 * *
 * * Licensed under the Apache License, Version 2.0 (the "License");
 * * you may not use this file except in compliance with the License.
 * * You may obtain a copy of the License at
 * *
 * * http://www.apache.org/licenses/LICENSE-2.0
 * *
 * * Unless required by applicable law or agreed to in writing, software
 * * distributed under the License is distributed on an "AS IS" BASIS,
 * * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * * See the License for the specific language governing permissions and
 * * limitations under the License.
 ******************************************************************************/
package com.impetus.blkch.sql.query;

import com.impetus.blkch.sql.parser.TreeNode;

public class StarNode extends TreeNode {

    public static final String DESCRIPTION = "STAR";

    private String tableIdentifier;

    public StarNode() {
        this(null);
    }

    public StarNode(String tableIdentifier) {
        super(DESCRIPTION + ":" + ((tableIdentifier != null) ? tableIdentifier + ".*" : "*"));
        this.tableIdentifier = tableIdentifier;
    }

    public String getTableIdentifier() {
        return tableIdentifier;
    }

    public void setTableIdentifier(String tableIdentifier) {
        this.tableIdentifier = tableIdentifier;
    }

}