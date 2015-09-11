/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Controller for Caches screen.
controlCenterModule.controller('cachesController', [
        '$scope', '$controller', '$http', '$timeout', '$common', '$focus', '$confirm', '$copy', '$table', '$preview',
        function ($scope, $controller, $http, $timeout, $common, $focus, $confirm, $copy, $table, $preview) {
            // Initialize the super class and extend it.
            angular.extend(this, $controller('save-remove', {$scope: $scope}));

            $scope.joinTip = $common.joinTip;
            $scope.getModel = $common.getModel;
            $scope.javaBuildInClasses = $common.javaBuildInClasses;
            $scope.compactJavaName = $common.compactJavaName;
            $scope.saveBtnTipText = $common.saveBtnTipText;

            $scope.tableReset = $table.tableReset;
            $scope.tableNewItem = $table.tableNewItem;
            $scope.tableNewItemActive = $table.tableNewItemActive;
            $scope.tableEditing = $table.tableEditing;
            $scope.tableStartEdit = $table.tableStartEdit;
            $scope.tableRemove = function (item, field, index) {
                $table.tableRemove(item, field, index);

                $common.markChanged($scope.ui.inputForm, 'cacheBackupItemChanged');
            };

            $scope.tableSimpleSave = $table.tableSimpleSave;
            $scope.tableSimpleSaveVisible = $table.tableSimpleSaveVisible;
            $scope.tableSimpleUp = $table.tableSimpleUp;
            $scope.tableSimpleDown = $table.tableSimpleDown;
            $scope.tableSimpleDownVisible = $table.tableSimpleDownVisible;

            $scope.tablePairSave = $table.tablePairSave;
            $scope.tablePairSaveVisible = $table.tablePairSaveVisible;

            var previews = [];

            $scope.previewInit = function (preview) {
                previews.push(preview);

                $preview.previewInit(preview);
            };

            $scope.previewChanged = $preview.previewChanged;

            $scope.formChanged = $common.formChanged;

            $scope.hidePopover = $common.hidePopover;

            var showPopoverMessage = $common.showPopoverMessage;

            $scope.atomicities = $common.mkOptions(['ATOMIC', 'TRANSACTIONAL']);

            $scope.cacheModes = $common.mkOptions(['PARTITIONED', 'REPLICATED', 'LOCAL']);

            $scope.atomicWriteOrderModes = $common.mkOptions(['CLOCK', 'PRIMARY']);

            $scope.memoryModes = $common.mkOptions(['ONHEAP_TIERED', 'OFFHEAP_TIERED', 'OFFHEAP_VALUES']);

            $scope.evictionPolicies = [
                {value: 'LRU', label: 'LRU'},
                {value: 'RND', label: 'Random'},
                {value: 'FIFO', label: 'FIFO'},
                {value: 'SORTED', label: 'Sorted'},
                {value: undefined, label: 'Not set'}
            ];

            $scope.rebalanceModes = $common.mkOptions(['SYNC', 'ASYNC', 'NONE']);

            $scope.cacheStoreFactories = [
                {value: 'CacheJdbcPojoStoreFactory', label: 'JDBC POJO store factory'},
                {value: 'CacheJdbcBlobStoreFactory', label: 'JDBC BLOB store factory'},
                {value: 'CacheHibernateBlobStoreFactory', label: 'Hibernate BLOB store factory'},
                {value: undefined, label: 'Not set'}
            ];

            $scope.cacheStoreJdbcDialects = [
                {value: 'Oracle', label: 'Oracle'},
                {value: 'DB2', label: 'IBM DB2'},
                {value: 'SQLServer', label: 'Microsoft SQL Server'},
                {value: 'MySQL', label: 'My SQL'},
                {value: 'PostgreSQL', label: 'Postgre SQL'},
                {value: 'H2', label: 'H2 database'}
            ];

            $scope.ui = {expanded: false};

            $scope.toggleExpanded = function () {
                $scope.ui.expanded = !$scope.ui.expanded;

                $common.hidePopover();
            };

            $scope.panels = {activePanels: [0]};

            $scope.general = [];
            $scope.advanced = [];

            $http.get('/models/caches.json')
                .success(function (data) {
                    $scope.screenTip = data.screenTip;
                    $scope.general = data.general;
                    $scope.advanced = data.advanced;
                })
                .error(function (errMsg) {
                    $common.showError(errMsg);
                });

            $scope.caches = [];
            $scope.metadatas = [];

            $scope.preview = {
                general: {xml: '', java: '', allDefaults: true},
                memory: {xml: '', java: '', allDefaults: true},
                query: {xml: '', java: '', allDefaults: true},
                store: {xml: '', java: '', allDefaults: true},
                concurrency: {xml: '', java: '', allDefaults: true},
                rebalance: {xml: '', java: '', allDefaults: true},
                serverNearCache: {xml: '', java: '', allDefaults: true},
                statistics: {xml: '', java: '', allDefaults: true}
            };

            $scope.required = function (field) {
                var model = $common.isDefined(field.path) ? field.path + '.' + field.model : field.model;

                var backupItem = $scope.backupItem;

                var memoryMode = backupItem.memoryMode;

                var onHeapTired = memoryMode == 'ONHEAP_TIERED';
                var offHeapTired = memoryMode == 'OFFHEAP_TIERED';

                var offHeapMaxMemory = backupItem.offHeapMaxMemory;

                if (model == 'offHeapMaxMemory' && offHeapTired)
                    return true;

                if (model == 'evictionPolicy.kind' && onHeapTired)
                    return backupItem.swapEnabled || ($common.isDefined(offHeapMaxMemory) && offHeapMaxMemory >= 0);

                return false;
            };

            $scope.tableSimpleValid = function (item, field, fx, index) {
                var model;

                switch (field.model) {
                    case 'hibernateProperties':
                        if (fx.indexOf('=') < 0)
                            return showPopoverMessage(null, null, $table.tableFieldId(index, 'HibProp'), 'Property should be present in format key=value!');

                        model = item.cacheStoreFactory.CacheHibernateBlobStoreFactory[field.model];

                        var key = fx.split('=')[0];

                        var exist = false;

                        if ($common.isDefined(model)) {
                            model.forEach(function (val) {
                                if (val.split('=')[0] == key)
                                    exist = true;
                            })
                        }

                        if (exist)
                            return showPopoverMessage(null, null, $table.tableFieldId(index, 'HibProp'), 'Property with such name already exists!');

                        break;

                    case 'sqlFunctionClasses':
                        if (!$common.isValidJavaClass('SQL function', fx, false, $table.tableFieldId(index, 'SqlFx')))
                            return $table.tableFocusInvalidField(index, 'SqlFx');

                        model = item[field.model];

                        if ($common.isDefined(model)) {
                            var idx = _.indexOf(model, fx);

                            // Found duplicate.
                            if (idx >= 0 && idx != index)
                                return showPopoverMessage(null, null, $table.tableFieldId(index, 'SqlFx'), 'SQL function with such class name already exists!');
                        }
                }

                return true;
            };

            $scope.tablePairValid = function (item, field, index) {
                var pairValue = $table.tablePairValue(field, index);

                if (!$common.isValidJavaClass('Indexed type key', pairValue.key, true, $table.tableFieldId(index, 'KeyIndexedType')))
                    return $table.tableFocusInvalidField(index, 'KeyIndexedType');

                if (!$common.isValidJavaClass('Indexed type value', pairValue.value, true, $table.tableFieldId(index, 'ValueIndexedType')))
                    return $table.tableFocusInvalidField(index, 'ValueIndexedType');

                var model = item[field.model];

                if ($common.isDefined(model)) {
                    var idx = _.findIndex(model, function (pair) {
                        return pair.keyClass == pairValue.key
                    });

                    // Found duplicate.
                    if (idx >= 0 && idx != index)
                        return showPopoverMessage(null, null, $table.tableFieldId(index, 'KeyIndexedType'), 'Indexed type with such key class already exists!');
                }

                return true;
            };

            // When landing on the page, get caches and show them.
            $http.post('caches/list')
                .success(function (data) {
                    $scope.spaces = data.spaces;
                    $scope.caches = data.caches;
                    $scope.clusters = data.clusters;

                    $scope.metadatas = _.map(data.metadatas, function (meta) {
                        return {value: meta._id, label: meta.valueType, kind: meta.kind, meta: meta}
                    });

                    var restoredItem = angular.fromJson(sessionStorage.cacheBackupItem);

                    if (restoredItem) {
                        restoredItem.metadatas = _.filter(restoredItem.metadatas, function (metaId) {
                            return _.findIndex($scope.metadatas, function (scopeMeta) {
                                    return scopeMeta.value == metaId;
                                }) >= 0;
                        });

                        if (restoredItem._id) {
                            var idx = _.findIndex($scope.caches, function (cache) {
                                return cache._id == restoredItem._id;
                            });

                            if (idx >= 0) {
                                var cache = $scope.caches[idx];

                                var restoredSelectedItem = angular.fromJson(sessionStorage.cacheSelectedItem);

                                // Clusters not changed by user. We should take clusters from server as they could be changed on Clusters screen.
                                if (restoredSelectedItem && _.isEqual(restoredItem.clusters, restoredSelectedItem.clusters)) {
                                    restoredItem.clusters = [];

                                    _.forEach(cache.clusters, function (cluster) {
                                        restoredItem.clusters.push(cluster)
                                    });
                                }
                                else {
                                    // Clusters changed by user. We need to remove deleted clusters (if any).
                                    restoredItem.clusters = _.filter(restoredItem.clusters, function (clusterId) {
                                        return _.findIndex($scope.clusters, function (scopeCluster) {
                                                return scopeCluster.value == clusterId;
                                            }) >= 0;
                                    });
                                }

                                // Metadatas not changed by user. We should take metadatas from server as they could be changed on Metadata screen.
                                if (restoredSelectedItem && _.isEqual(restoredItem.metadatas, restoredSelectedItem.metadatas)) {
                                    restoredItem.metadatas = [];

                                    _.forEach(cache.metadatas, function (meta) {
                                        restoredItem.metadatas.push(meta)
                                    });
                                }
                                else {
                                    // Metadatas changed by user. We need to remove deleted metadatas (if any).
                                    restoredItem.metadatas = _.filter(restoredItem.metadatas, function (metaId) {
                                        return _.findIndex($scope.metadatas, function (scopeMeta) {
                                                return scopeMeta.value == metaId;
                                            }) >= 0;
                                    });
                                }

                                $scope.selectItem(cache, restoredItem, sessionStorage.cacheBackupItemChanged);
                            }
                            else
                                sessionStorage.removeItem('cacheBackupItem');
                        }
                        else
                            $scope.selectItem(undefined, restoredItem, sessionStorage.cacheBackupItemChanged)
                    }
                    else if ($scope.caches.length > 0)
                        $scope.selectItem($scope.caches[0]);

                    function isStoreFactoryDefined(cache) {
                        return $common.isDefined(cache)
                            && $common.isDefined(cache.cacheStoreFactory)
                            && $common.isDefined(cache.cacheStoreFactory.kind);
                    }

                    $scope.$watch('backupItem', function (val) {
                        if (val) {
                            // Collect cache metadatas.
                            var cacheMetadatas = _.reduce($scope.metadatas, function(memo, meta){
                                if (_.contains(val.metadatas, meta.value)) {
                                    memo.push(meta.meta);
                                }

                                return memo;
                            }, []);

                            var prevVal = angular.fromJson(sessionStorage.cacheBackupItem);
                            var prevCacheStore = isStoreFactoryDefined(prevVal);
                            var newCacheStore = isStoreFactoryDefined(val);

                            if (!prevCacheStore && !newCacheStore) {
                                if (_.findIndex(cacheMetadatas, $common.metadataForStoreConfigured) >= 0) {
                                    val.cacheStoreFactory.kind = 'CacheJdbcPojoStoreFactory';

                                    if (!val.readThrough && !val.writeThrough) {
                                        val.readThrough = true;
                                        val.writeThrough = true;
                                    }

                                    $timeout(function () {
                                        $common.ensureActivePanel($scope.panels, 'store');
                                    });
                                }
                            }

                            var varName = 'cache';

                            $scope.preview.general.xml = $generatorXml.cacheMetadatas(cacheMetadatas, $generatorXml.cacheGeneral(val)).asString();
                            $scope.preview.general.java = $generatorJava.cacheMetadatas(cacheMetadatas, varName, $generatorJava.cacheGeneral(val, varName)).asString();
                            $scope.preview.general.allDefaults = $common.isEmptyString($scope.preview.general.xml);

                            $scope.preview.memory.xml = $generatorXml.cacheMemory(val).asString();
                            $scope.preview.memory.java = $generatorJava.cacheMemory(val, varName).asString();
                            $scope.preview.memory.allDefaults = $common.isEmptyString($scope.preview.memory.xml);

                            $scope.preview.query.xml = $generatorXml.cacheQuery(val).asString();
                            $scope.preview.query.java = $generatorJava.cacheQuery(val, varName).asString();
                            $scope.preview.query.allDefaults = $common.isEmptyString($scope.preview.query.xml);

                            $scope.preview.store.xml = $generatorXml.cacheStore(val).asString();
                            $scope.preview.store.java = $generatorJava.cacheStore(val, varName).asString();
                            $scope.preview.store.allDefaults = $common.isEmptyString($scope.preview.store.xml);

                            $scope.preview.concurrency.xml = $generatorXml.cacheConcurrency(val).asString();
                            $scope.preview.concurrency.java = $generatorJava.cacheConcurrency(val, varName).asString();
                            $scope.preview.concurrency.allDefaults = $common.isEmptyString($scope.preview.concurrency.xml);

                            $scope.preview.rebalance.xml = $generatorXml.cacheRebalance(val).asString();
                            $scope.preview.rebalance.java = $generatorJava.cacheRebalance(val, varName).asString();
                            $scope.preview.rebalance.allDefaults = $common.isEmptyString($scope.preview.rebalance.xml);

                            $scope.preview.serverNearCache.xml = $generatorXml.cacheServerNearCache(val).asString();
                            $scope.preview.serverNearCache.java = $generatorJava.cacheServerNearCache(val, varName).asString();
                            $scope.preview.serverNearCache.allDefaults = $common.isEmptyString($scope.preview.serverNearCache.xml);

                            $scope.preview.statistics.xml = $generatorXml.cacheStatistics(val).asString();
                            $scope.preview.statistics.java = $generatorJava.cacheStatistics(val, varName).asString();
                            $scope.preview.statistics.allDefaults = $common.isEmptyString($scope.preview.statistics.xml);

                            sessionStorage.cacheBackupItem = angular.toJson(val);

                            $common.markChanged($scope.ui.inputForm, 'cacheBackupItemChanged');
                        }
                    }, true);
               })
                .error(function (errMsg) {
                    $common.showError(errMsg);
                });

            $scope.selectItem = function (item, backup, changed) {
                function selectItem() {
                    $table.tableReset();

                    $scope.selectedItem = item;

                    if (item)
                        sessionStorage.cacheSelectedItem = angular.toJson(item);
                    else
                        sessionStorage.removeItem('cacheSelectedItem');

                    _.forEach(previews, function(preview) {
                        preview.attractAttention = false;
                    });

                    if (backup)
                        $scope.backupItem = backup;
                    else if (item)
                        $scope.backupItem = angular.copy(item);
                    else
                        $scope.backupItem = undefined;

                    $timeout(function () {
                        if (changed)
                            $common.markChanged($scope.ui.inputForm, 'cacheBackupItemChanged');
                        else
                            $common.markPristine($scope.ui.inputForm, 'cacheBackupItemChanged');
                    }, 50);
                }

                $common.confirmUnsavedChanges($confirm, $scope.ui.inputForm, selectItem);

                $scope.ui.formTitle = $common.isDefined($scope.backupItem) && $scope.backupItem._id ?
                    'Selected cache: ' + $scope.backupItem.name : 'New cache';
            };

            // Add new cache.
            $scope.createItem = function () {
                $table.tableReset();

                $timeout(function () {
                    $common.ensureActivePanel($scope.panels, 'general', 'cacheName');
                });

                var newItem = {
                    space: $scope.spaces[0]._id,
                    cacheMode: 'PARTITIONED',
                    atomicityMode: 'ATOMIC',
                    readFromBackup: true,
                    copyOnRead: true,
                    clusters: [],
                    metadatas: []
                };

                $scope.selectItem(undefined, newItem);
            };

            // Check cache logical consistency.
            function validate(item) {
                if ($common.isEmptyString(item.name))
                    return showPopoverMessage($scope.panels, 'general', 'cacheName', 'Name should not be empty');

                if (item.memoryMode == 'OFFHEAP_TIERED' && item.offHeapMaxMemory == null)
                    return showPopoverMessage($scope.panels, 'memory', 'offHeapMaxMemory',
                        'Off-heap max memory should be specified');

                var cacheStoreFactorySelected = item.cacheStoreFactory && item.cacheStoreFactory.kind;

                if (cacheStoreFactorySelected) {
                    if (item.cacheStoreFactory.kind == 'CacheJdbcPojoStoreFactory') {
                        if ($common.isEmptyString(item.cacheStoreFactory.CacheJdbcPojoStoreFactory.dataSourceBean))
                            return showPopoverMessage($scope.panels, 'store', 'dataSourceBean',
                                'Data source bean should not be empty');

                        if (!item.cacheStoreFactory.CacheJdbcPojoStoreFactory.dialect)
                            return showPopoverMessage($scope.panels, 'store', 'dialect',
                                'Dialect should not be empty');
                    }

                    if (item.cacheStoreFactory.kind == 'CacheJdbcBlobStoreFactory') {
                        if ($common.isEmptyString(item.cacheStoreFactory.CacheJdbcBlobStoreFactory.user))
                            return showPopoverMessage($scope.panels, 'store', 'user',
                                'User should not be empty');

                        if ($common.isEmptyString(item.cacheStoreFactory.CacheJdbcBlobStoreFactory.dataSourceBean))
                            return showPopoverMessage($scope.panels, 'store', 'dataSourceBean',
                                'Data source bean should not be empty');
                    }
                }

                if ((item.readThrough || item.writeThrough) && !cacheStoreFactorySelected)
                    return showPopoverMessage($scope.panels, 'store', 'cacheStoreFactory',
                        (item.readThrough ? 'Read' : 'Write') + ' through are enabled but store is not configured!');

                if (item.writeBehindEnabled && !cacheStoreFactorySelected)
                    return showPopoverMessage($scope.panels, 'store', 'cacheStoreFactory',
                        'Write behind enabled but store is not configured!');

                if (cacheStoreFactorySelected && !(item.readThrough || item.writeThrough))
                    return showPopoverMessage($scope.panels, 'store', 'readThrough',
                        'Store is configured but read/write through are not enabled!');

                return true;
            }

            // Save cache into database.
            function save(item) {
                $http.post('caches/save', item)
                    .success(function (_id) {
                        $common.markPristine($scope.ui.inputForm, 'cacheBackupItemChanged');

                        var idx = _.findIndex($scope.caches, function (cache) {
                            return cache._id == _id;
                        });

                        if (idx >= 0)
                            angular.extend($scope.caches[idx], item);
                        else {
                            item._id = _id;

                            $scope.caches.push(item);
                        }

                        $scope.selectItem(item);

                        $common.showInfo('Cache "' + item.name + '" saved.');
                    })
                    .error(function (errMsg) {
                        $common.showError(errMsg);
                    });
            }

            // Save cache.
            $scope.saveItem = function () {
                $table.tableReset();

                var item = $scope.backupItem;

                if (validate(item))
                    save(item);
            };

            // Save cache with new name.
            $scope.copyItem = function () {
                $table.tableReset();

                if (validate($scope.backupItem))
                    $copy.show($scope.backupItem.name).then(function (newName) {
                        var item = angular.copy($scope.backupItem);

                        item._id = undefined;
                        item.name = newName;

                        save(item);
                    });
            };

            // Remove cache from db.
            $scope.removeItem = function () {
                $table.tableReset();

                var selectedItem = $scope.selectedItem;

                $confirm.show('Are you sure you want to remove cache: "' + selectedItem.name + '"?').then(
                    function () {
                        $common.markPristine($scope.ui.inputForm, 'cacheBackupItemChanged');

                        var _id = selectedItem._id;

                        $http.post('caches/remove', {_id: _id})
                            .success(function () {
                                $common.showInfo('Cache has been removed: ' + selectedItem.name);

                                var caches = $scope.caches;

                                var idx = _.findIndex(caches, function (cache) {
                                    return cache._id == _id;
                                });

                                if (idx >= 0) {
                                    caches.splice(idx, 1);

                                    if (caches.length > 0)
                                        $scope.selectItem(caches[0]);
                                    else
                                        $scope.selectItem(undefined, undefined);
                                }
                            })
                            .error(function (errMsg) {
                                $common.showError(errMsg);
                            });
                    }
                );
            };

            // Remove all caches from db.
            $scope.removeAllItems = function () {
                $table.tableReset();

                $confirm.show('Are you sure you want to remove all caches?').then(
                    function () {
                        $common.markPristine($scope.ui.inputForm, 'cacheBackupItemChanged');

                        $http.post('caches/remove/all')
                            .success(function () {
                                $common.showInfo('All caches have been removed');

                                $scope.caches = [];

                                $scope.selectItem(undefined, undefined);
                            })
                            .error(function (errMsg) {
                                $common.showError(errMsg);
                            });
                    }
                );
            };
        }]
);
