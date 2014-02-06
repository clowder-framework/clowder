/**
 *
 */
package services.mongodb

import services.DatasetService

/**
 * Use Mongodb to store datasets.
 * 
 * @author Luigi Marini
 *
 */
class MongoDBDatasetService extends DatasetService with MongoDBDataset {
}