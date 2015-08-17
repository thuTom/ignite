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

var _ = require('lodash');
var router = require('express').Router();
var db = require('../db');

/* GET caches page. */
router.get('/', function (req, res) {
    res.render('configuration/caches');
});

function _processed(err, res) {
    if (err) {
        res.status(500).send(err.message);

        return false;
    }

    return true;
}

/**
 * Get spaces and caches accessed for user account.
 *
 * @param req Request.
 * @param res Response.
 */
router.post('/list', function (req, res) {
    var user_id = req.currentUserId();

    // Get owned space and all accessed space.
    db.Space.find({$or: [{owner: user_id}, {usedBy: {$elemMatch: {account: user_id}}}]}, function (err, spaces) {
        if (_processed(err, res)) {
            var space_ids = spaces.map(function (value) {
                return value._id;
            });

            // Get all clusters for spaces.
            db.Cluster.find({space: {$in: space_ids}}, '_id name', function (err, clusters) {
                if (_processed(err, res)) {
                    // Get all caches type metadata for spaces.
                    db.CacheTypeMetadata.find({space: {$in: space_ids}}, '_id name kind', function (err, metadatas) {
                        if (_processed(err, res)) {
                            // Get all caches for spaces.
                            db.Cache.find({space: {$in: space_ids}}).sort('name').exec(function (err, caches) {
                                if (_processed(err, res)) {
                                    // Remove deleted metadata.
                                    _.forEach(caches, function (cache) {
                                        cache.queryMetadata = _.filter(cache.queryMetadata, function (metaId) {
                                            return _.findIndex(metadatas, function (meta) {
                                                    return meta._id.equals(metaId);
                                                }) >= 0;
                                        });

                                        cache.storeMetadata = _.filter(cache.storeMetadata, function (metaId) {
                                            return _.findIndex(metadatas, function (meta) {
                                                    return meta._id.equals(metaId);
                                                }) >= 0;
                                        });
                                    });

                                    res.json({
                                        spaces: spaces,
                                        clusters: clusters.map(function(cluster) {
                                            return {value: cluster._id, label: cluster.name};
                                        }),
                                        metadatas: metadatas.map(function (meta) {
                                            return {value: meta._id, label: meta.name, kind: meta.kind};
                                        }),
                                        caches: caches});
                                }
                            });
                        }
                    });
                }
            });
        }
    });
});

/**
 * Save cache.
 */
router.post('/save', function (req, res) {
    var params = req.body;
    var cacheId = params._id;

    if (params._id)
        db.Cache.update({_id: cacheId}, params, {upsert: true}, function (err, cache) {
            if (_processed(err, res)) {
                //_.forEach(params.clusters, function (cluster) {
                //    db.Cluster.findOne({_id: cluster}, function (err, cluster) {
                //        if (_processed(err, res))
                //            cluster.caches.push(cacheId);
                //
                //            db.Cluster.update({_id: params._id}, cluster, {upsert: true}, function(err) {
                //                _processed(err, res);
                //            });
                //    });
                //});

                res.send(params._id);
            }
        });
    else
        db.Cache.findOne({space: params.space, name: params.name}, function (err, cache) {
            if (_processed(err, res)) {
                if (cache)
                    return res.status(500).send('Cache with name: "' + cache.name + '" already exist.');

                (new db.Cache(params)).save(function (err, cache) {
                    if (_processed(er, res))
                        res.send(cache._id);
                });
            }
        });
});

/**
 * Remove cache by ._id.
 */
router.post('/remove', function (req, res) {
    db.Cache.remove(req.body, function (err) {
        if (_processed(err, res))
            res.sendStatus(200);
    })
});

module.exports = router;