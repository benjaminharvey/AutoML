package com.databricks.spark.automatedml.model.tools.structures

import com.databricks.spark.automatedml.params._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._

import scala.collection.mutable.ArrayBuffer
import scala.reflect.runtime.universe._

trait ModelConfigGenerators extends SeedGenerator {

  /**
    * Helper method for reading a case class definition, getting the defined names of each key, and returning them as
    * an iterable list.
    * @tparam T The class type as derived through reflection
    * @return The List of all case class member names
    */
  def getCaseClassNames[T: TypeTag]: List[String] = typeOf[T].members.sorted.collect {
    case m: MethodSymbol if m.isCaseAccessor => m.name.toString
  }


  // RANDOM FOREST METHODS
  /**
    * Method for taking a collection of permutations generated per each hyper parameter and converting them
    * into a collection that can be used to execute models by building out all possible permutations of the generated
    * hyper parameter collections.
    * @param randomForestPermutationCollection The Array of values generated for possible hyper parameters for the
    *                                          permutation collection creation
    * @return Array of Random Forest configurations based on permutations of each value within the arrays supplied.
    */
  def randomForestConfigGenerator(randomForestPermutationCollection: RandomForestPermutationCollection):
  Array[RandomForestConfig] = {

    for {
      numTrees <- randomForestPermutationCollection.numTreesArray
      impurity <- randomForestPermutationCollection.impurityArray
      maxBins <- randomForestPermutationCollection.maxBinsArray
      maxDepth <- randomForestPermutationCollection.maxDepthArray
      minInfoGain <- randomForestPermutationCollection.minInfoGainArray
      subSamplingRate <- randomForestPermutationCollection.subSamplingRateArray
      featureSubsetStrategy <- randomForestPermutationCollection.featureSubsetStrategyArray
    } yield RandomForestConfig(numTrees.toInt, impurity, maxBins.toInt, maxDepth.toInt, minInfoGain, subSamplingRate,
      featureSubsetStrategy)
  }

  /**
    * Method for generating linear and log spaces for potential hyper parameter values for the model
    * @param config Configuration value for the generation of permutation arrays
    * @return Arrays for all numeric parameters that will be generated for input into the permutation generator
    */
  protected[tools] def randomForestNumericArrayGenerator(config: PermutationConfiguration):
  RandomForestNumericArrays = {

    RandomForestNumericArrays(
      numTreesArray = generateLinearIntSpace(extractContinuousBoundaries(config.numericBoundaries("numTrees")),
        config.permutationTarget),
      maxBinsArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxBins")), config.permutationTarget),
      maxDepthArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxDepth")), config.permutationTarget),
      minInfoGainArray = generateLogSpace(
        extractContinuousBoundaries(config.numericBoundaries("minInfoGain")), config.permutationTarget),
      subSamplingRateArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("subSamplingRate")), config.permutationTarget)
    )
  }

  /**
    * Main accessor for generating permutations for a RandomForest Model
    * @param config Configuration for holding the numeber of permutations to generate and the boundaries of the
    *               search space
    * @param countTarget Total maximum count of permutations to return
    * @param seed Seed for determining the random sample of permutations that are generated due to the sheer count
    *             of permutations that are generated to search the space effectively.
    * @return An Array of RandomForest Configurations to be used in generating model runs.
    */
  def randomForestPermutationGenerator(config: PermutationConfiguration, countTarget: Int, seed: Long = 42L):
  Array[RandomForestConfig] = {

    // Get the number of permutations to generate
    val numericPayloads = randomForestNumericArrayGenerator(config)

    val fullPermutationConfig = RandomForestPermutationCollection(
      numTreesArray = numericPayloads.numTreesArray,
      maxBinsArray = numericPayloads.maxBinsArray,
      maxDepthArray = numericPayloads.maxDepthArray,
      minInfoGainArray = numericPayloads.minInfoGainArray,
      subSamplingRateArray = numericPayloads.subSamplingRateArray,
      impurityArray = config.stringBoundaries("impurity").toArray,
      featureSubsetStrategyArray = config.stringBoundaries("featureSubsetStrategy").toArray
    )

    val permutationCollection = randomForestConfigGenerator(fullPermutationConfig)

    randomSampleArray(permutationCollection, countTarget, seed)

  }

  /**
    * Helper method for converting a Dataframe of predicted hyper parameters into configurations that can be used
    * by models (for post-run hyper parameter optimization)
    * @param predictionDataFrame The predicted sets of highest probability hyper parameter collections
    * @return An Array of RandomForest Configurations to be used in generating model runs.
    */
  def convertRandomForestResultToConfig(predictionDataFrame: DataFrame): Array[RandomForestConfig] = {

    val collectionBuffer = new ArrayBuffer[RandomForestConfig]()

    val dataCollection = predictionDataFrame
      .select(getCaseClassNames[RandomForestConfig] map col :_*)
      .collect()

    dataCollection.foreach{ x =>

      collectionBuffer += RandomForestConfig(
        numTrees = x(0).toString.toInt,
        impurity = x(1).toString,
        maxBins = x(2).toString.toInt,
        maxDepth = x(3).toString.toInt,
        minInfoGain = x(4).toString.toDouble,
        subSamplingRate = x(5).toString.toDouble,
        featureSubsetStrategy = x(6).toString
      )

    }

    collectionBuffer.result.toArray
  }

  // DECISION TREE METHODS
  def treesConfigGenerator(treesPermutationCollection: TreesPermutationCollection):
  Array[TreesConfig] = {

    for {
      impurity <- treesPermutationCollection.impurityArray
      maxBins <- treesPermutationCollection.maxBinsArray
      maxDepth <- treesPermutationCollection.maxDepthArray
      minInfoGain <- treesPermutationCollection.minInfoGainArray
      minInstancesPerNode <- treesPermutationCollection.minInstancesPerNodeArray
    } yield TreesConfig(impurity, maxBins.toInt, maxDepth.toInt, minInfoGain, minInstancesPerNode.toInt)
  }

  protected[tools] def treesNumericArrayGenerator(config: PermutationConfiguration):
  TreesNumericArrays = {

    TreesNumericArrays(
      maxBinsArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxBins")), config.permutationTarget),
      maxDepthArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxDepth")), config.permutationTarget),
      minInfoGainArray = generateLogSpace(
        extractContinuousBoundaries(config.numericBoundaries("minInfoGain")), config.permutationTarget),
      minInstancesPerNodeArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("minInstancesPerNode")), config.permutationTarget)
    )
  }

  def treesPermutationGenerator(config: PermutationConfiguration, countTarget: Int, seed: Long = 42L):
  Array[TreesConfig] = {

    // Get the number of permutations to generate
    val numericPayloads = treesNumericArrayGenerator(config)

    val fullPermutationConfig = TreesPermutationCollection(
      impurityArray = config.stringBoundaries("impurity").toArray,
      maxBinsArray = numericPayloads.maxBinsArray,
      maxDepthArray = numericPayloads.maxDepthArray,
      minInfoGainArray = numericPayloads.minInfoGainArray,
      minInstancesPerNodeArray = numericPayloads.minInstancesPerNodeArray
    )

    val permutationCollection = treesConfigGenerator(fullPermutationConfig)

    randomSampleArray(permutationCollection, countTarget, seed)

  }

  def convertTreesResultToConfig(predictionDataFrame: DataFrame): Array[TreesConfig] = {

    val collectionBuffer = new ArrayBuffer[TreesConfig]()

    val dataCollection = predictionDataFrame
      .select(getCaseClassNames[TreesConfig] map col :_*)
      .collect()

    dataCollection.foreach{ x =>

      collectionBuffer += TreesConfig(
        impurity = x(0).toString,
        maxBins = x(1).toString.toInt,
        maxDepth = x(2).toString.toInt,
        minInfoGain = x(3).toString.toDouble,
        minInstancesPerNode = x(4).toString.toInt
      )

    }
    collectionBuffer.result.toArray
  }

  // GRADIENT BOOSTED TREES METHODS

  def gbtConfigGenerator(gbtPermutationCollection: GBTPermutationCollection):
  Array[GBTConfig] = {

    for {
      impurity <- gbtPermutationCollection.impurityArray
      lossType <- gbtPermutationCollection.lossTypeArray
      maxBins <- gbtPermutationCollection.maxBinsArray
      maxDepth <- gbtPermutationCollection.maxDepthArray
      maxIter <- gbtPermutationCollection.maxIterArray
      minInfoGain <- gbtPermutationCollection.minInfoGainArray
      minInstancesPerNode <- gbtPermutationCollection.minInstancesPerNodeArray
      stepSize <- gbtPermutationCollection.stepSizeArray
    } yield GBTConfig(impurity, lossType, maxBins.toInt, maxDepth.toInt, maxIter.toInt, minInfoGain,
      minInstancesPerNode.toInt, stepSize)
  }

  protected[tools] def gbtNumericArrayGenerator(config: PermutationConfiguration):
  GBTNumericArrays = {

    GBTNumericArrays(
      maxBinsArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxBins")), config.permutationTarget),
      maxDepthArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxDepth")), config.permutationTarget),
      maxIterArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxIter")), config.permutationTarget),
      minInfoGainArray = generateLogSpace(
        extractContinuousBoundaries(config.numericBoundaries("minInfoGain")), config.permutationTarget),
      minInstancesPerNodeArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("minInstancesPerNode")), config.permutationTarget),
      stepSizeArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("stepSize")), config.permutationTarget)
    )
  }

  def gbtPermutationGenerator(config: PermutationConfiguration, countTarget: Int, seed: Long = 42L):
  Array[GBTConfig] = {

    // Get the number of permutations to generate
    val numericPayloads = gbtNumericArrayGenerator(config)

    val fullPermutationConfig = GBTPermutationCollection(
      impurityArray = config.stringBoundaries("impurity").toArray,
      lossTypeArray = config.stringBoundaries("lossType").toArray,
      maxBinsArray = numericPayloads.maxBinsArray,
      maxDepthArray = numericPayloads.maxDepthArray,
      maxIterArray = numericPayloads.maxIterArray,
      minInfoGainArray = numericPayloads.minInfoGainArray,
      minInstancesPerNodeArray = numericPayloads.minInstancesPerNodeArray,
      stepSizeArray = numericPayloads.stepSizeArray
    )

    val permutationCollection = gbtConfigGenerator(fullPermutationConfig)

    randomSampleArray(permutationCollection, countTarget, seed)

  }

  def convertGBTResultToConfig(predictionDataFrame: DataFrame): Array[GBTConfig] = {

    val collectionBuffer = new ArrayBuffer[GBTConfig]()

    val dataCollection = predictionDataFrame
      .select(getCaseClassNames[GBTConfig] map col :_*)
        .collect()

    dataCollection.foreach{ x =>
      collectionBuffer += GBTConfig(
        impurity = x(0).toString,
        lossType = x(1).toString,
        maxBins = x(2).toString.toInt,
        maxDepth = x(3).toString.toInt,
        maxIter = x(4).toString.toInt,
        minInfoGain = x(5).toString.toDouble,
        minInstancesPerNode = x(6).toString.toInt,
        stepSize = x(7).toString.toDouble
      )
    }
    collectionBuffer.result.toArray
  }

  // LINEAR REGRESSION METHODS
  def linearRegressionConfigGenerator(linearRegressionPermutationCollection: LinearRegressionPermutationCollection):
  Array[LinearRegressionConfig] = {

    for {
      elasticNetParams <- linearRegressionPermutationCollection.elasticNetParamsArray
      fitIntercept <- linearRegressionPermutationCollection.fitInterceptArray
      loss <- linearRegressionPermutationCollection.lossArray
      maxIter <- linearRegressionPermutationCollection.maxIterArray
      regParam <- linearRegressionPermutationCollection.regParamArray
      standardization <- linearRegressionPermutationCollection.standardizationArray
      tolerance <- linearRegressionPermutationCollection.toleranceArray
    } yield LinearRegressionConfig(elasticNetParams, fitIntercept, loss, maxIter.toInt, regParam, standardization,
      tolerance)
  }

  protected[tools] def linearRegressionNumericArrayGenerator(config: PermutationConfiguration):
  LinearRegressionNumericArrays = {

    LinearRegressionNumericArrays(
      elasticNetParamsArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("elasticNetParams")), config.permutationTarget),
      maxIterArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxIter")), config.permutationTarget),
      regParamArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("regParam")), config.permutationTarget),
      toleranceArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("tolerance")), config.permutationTarget)
    )
  }

  def linearRegressionPermutationGenerator(config: PermutationConfiguration, countTarget: Int, seed: Long = 42L):
  Array[LinearRegressionConfig] = {

    // Get the number of permutations to generate
    val numericPayloads = linearRegressionNumericArrayGenerator(config)

    val fullPermutationConfig = LinearRegressionPermutationCollection(
      elasticNetParamsArray = numericPayloads.elasticNetParamsArray,
      fitInterceptArray = Array(true, false),
      lossArray = config.stringBoundaries("loss").toArray,
      maxIterArray = numericPayloads.maxIterArray,
      regParamArray = numericPayloads.regParamArray,
      standardizationArray = Array(true, false),
      toleranceArray = numericPayloads.toleranceArray
    )

    val permutationCollection = linearRegressionConfigGenerator(fullPermutationConfig)

    randomSampleArray(permutationCollection, countTarget, seed)
  }

  def convertLinearRegressionResultToConfig(predictionDataFrame: DataFrame): Array[LinearRegressionConfig] = {

    val collectionBuffer = new ArrayBuffer[LinearRegressionConfig]()

    val dataCollection = predictionDataFrame
      .select(getCaseClassNames[LinearRegressionConfig] map col :_*)
        .collect()

    dataCollection.foreach{ x =>
      collectionBuffer += LinearRegressionConfig(
        elasticNetParams = x(0).toString.toDouble,
        fitIntercept = x(1).toString.toBoolean,
        loss = x(2).toString,
        maxIter = x(3).toString.toInt,
        regParam = x(4).toString.toDouble,
        standardization = x(5).toString.toBoolean,
        tolerance = x(6).toString.toDouble
      )
    }
    collectionBuffer.result.toArray
  }

  // LOGISTIC REGRESSION METHODS
  def logisticRegressionConfigGenerator(logisticRegressionPermutationCollection: LogisticRegressionPermutationCollection):
  Array[LogisticRegressionConfig] = {

    for {
      elasticNetParams <- logisticRegressionPermutationCollection.elasticNetParamsArray
      fitIntercept <- logisticRegressionPermutationCollection.fitInterceptArray
      maxIter <- logisticRegressionPermutationCollection.maxIterArray
      regParam <- logisticRegressionPermutationCollection.regParamArray
      standardization <- logisticRegressionPermutationCollection.standardizationArray
      tolerance <- logisticRegressionPermutationCollection.toleranceArray
    } yield LogisticRegressionConfig(elasticNetParams, fitIntercept, maxIter.toInt, regParam, standardization,
      tolerance)
  }

  protected[tools] def logisticRegressionNumericArrayGenerator(config: PermutationConfiguration):
  LogisticRegressionNumericArrays = {

    LogisticRegressionNumericArrays(
      elasticNetParamsArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("elasticNetParams")), config.permutationTarget),
      maxIterArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxIter")), config.permutationTarget),
      regParamArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("regParam")), config.permutationTarget),
      toleranceArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("tolerance")), config.permutationTarget)
    )
  }

  def logisticRegressionPermutationGenerator(config: PermutationConfiguration, countTarget: Int, seed: Long = 42L):
  Array[LogisticRegressionConfig] = {

    // Get the number of permutations to generate
    val numericPayloads = logisticRegressionNumericArrayGenerator(config)

    val fullPermutationConfig = LogisticRegressionPermutationCollection(
      elasticNetParamsArray = numericPayloads.elasticNetParamsArray,
      fitInterceptArray = Array(true, false),
      maxIterArray = numericPayloads.maxIterArray,
      regParamArray = numericPayloads.regParamArray,
      standardizationArray = Array(true, false),
      toleranceArray = numericPayloads.toleranceArray
    )

    val permutationCollection = logisticRegressionConfigGenerator(fullPermutationConfig)

    randomSampleArray(permutationCollection, countTarget, seed)

  }

  def convertLogisticRegressionResultToConfig(predictionDataFrame: DataFrame): Array[LogisticRegressionConfig] = {

    val collectionBuffer = new ArrayBuffer[LogisticRegressionConfig]()

    val dataCollection = predictionDataFrame
      .select(getCaseClassNames[LogisticRegressionConfig] map col :_*)
      .collect()

    dataCollection.foreach{ x =>
      collectionBuffer += LogisticRegressionConfig(
        elasticNetParams = x(0).toString.toDouble,
        fitIntercept = x(1).toString.toBoolean,
        maxIter = x(2).toString.toInt,
        regParam = x(3).toString.toDouble,
        standardization = x(4).toString.toBoolean,
        tolerance = x(5).toString.toDouble
      )
    }
    collectionBuffer.result.toArray
  }

  // SUPPORT VECTOR MACHINE METHODS
  def svmConfigGenerator(svmPermutationCollection: SVMPermutationCollection): Array[SVMConfig] = {

    for {
      fitIntercept <- svmPermutationCollection.fitInterceptArray
      maxIter <- svmPermutationCollection.maxIterArray
      regParam <- svmPermutationCollection.regParamArray
      standardization <- svmPermutationCollection.standardizationArray
      tol <- svmPermutationCollection.tolArray
    } yield SVMConfig(fitIntercept, maxIter.toInt, regParam, standardization, tol)
  }

  protected[tools] def svmNumericArrayGenerator(config: PermutationConfiguration): SVMNumericArrays = {

    SVMNumericArrays(
      maxIterArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxIter")), config.permutationTarget),
      regParamArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("regParam")), config.permutationTarget),
      tolArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("tol")), config.permutationTarget)
    )
  }

  def svmPermutationGenerator(config: PermutationConfiguration, countTarget: Int, seed: Long = 42L):
  Array[SVMConfig] = {

    // Get the number of permutations to generate
    val numericPayloads = svmNumericArrayGenerator(config)

    val fullPermutationConfig = SVMPermutationCollection(
      fitInterceptArray = Array(true, false),
      maxIterArray = numericPayloads.maxIterArray,
      regParamArray = numericPayloads.regParamArray,
      standardizationArray = Array(true, false),
      tolArray = numericPayloads.tolArray
    )

    val permutationCollection = svmConfigGenerator(fullPermutationConfig)

    randomSampleArray(permutationCollection, countTarget, seed)

  }

  def convertSVMResultToConfig(predictionDataFrame: DataFrame): Array[SVMConfig] = {

    val collectionBuffer = new ArrayBuffer[SVMConfig]()

    val dataCollection = predictionDataFrame
      .select(getCaseClassNames[SVMConfig] map col :_*)
      .collect()

    dataCollection.foreach{ x =>
      collectionBuffer += SVMConfig(
        fitIntercept = x(0).toString.toBoolean,
        maxIter = x(1).toString.toInt,
        regParam = x(2).toString.toDouble,
        standardization = x(3).toString.toBoolean,
        tol = x(4).toString.toDouble
      )
    }
    collectionBuffer.result.toArray
  }

  // XGBOOST METHODS
  def xgboostConfigGenerator(xgboostPermutationCollection: XGBoostPermutationCollection): Array[XGBoostConfig] = {

    for {
      alpha <- xgboostPermutationCollection.alphaArray
      eta <- xgboostPermutationCollection.etaArray
      gamma <- xgboostPermutationCollection.gammaArray
      lambda <- xgboostPermutationCollection.lambdaArray
      maxDepth <- xgboostPermutationCollection.maxDepthArray
      subSample <- xgboostPermutationCollection.subSampleArray
      minChildWeight <- xgboostPermutationCollection.minChildWeightArray
      numRound <- xgboostPermutationCollection.numRoundArray
      maxBins <- xgboostPermutationCollection.maxBinsArray
      trainTestRatio <- xgboostPermutationCollection.trainTestRatioArray
    } yield XGBoostConfig(alpha, eta, gamma, lambda, maxDepth.toInt, subSample, minChildWeight, numRound.toInt,
      maxBins.toInt, trainTestRatio)
  }

  protected[tools] def xgboostNumericArrayGenerator(config: PermutationConfiguration): XGBoostNumericArrays = {

    XGBoostNumericArrays(
      alphaArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("alpha")), config.permutationTarget),
      etaArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("eta")), config.permutationTarget),
      gammaArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("gamma")), config.permutationTarget),
      lambdaArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("lambda")), config.permutationTarget),
      maxDepthArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxDepth")), config.permutationTarget),
      subSampleArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("subSample")), config.permutationTarget),
      minChildWeightArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("minChildWeight")), config.permutationTarget),
      numRoundArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("numRound")), config.permutationTarget),
      maxBinsArray = generateLinearIntSpace(
        extractContinuousBoundaries(config.numericBoundaries("maxBins")), config.permutationTarget),
      trainTestRatioArray = generateLinearSpace(
        extractContinuousBoundaries(config.numericBoundaries("trainTestRatio")), config.permutationTarget)
    )
  }

  def xgboostPermutationGenerator(config: PermutationConfiguration, countTarget: Int, seed: Long = 42L):
  Array[XGBoostConfig] = {

    // Get the number of permutations to generate
    val numericPayloads = xgboostNumericArrayGenerator(config)

    val fullPermutationConfig = XGBoostPermutationCollection(
      alphaArray = numericPayloads.alphaArray,
      etaArray = numericPayloads.etaArray,
      gammaArray = numericPayloads.gammaArray,
      lambdaArray = numericPayloads.lambdaArray,
      maxDepthArray = numericPayloads.maxDepthArray,
      subSampleArray = numericPayloads.subSampleArray,
      minChildWeightArray = numericPayloads.minChildWeightArray,
      numRoundArray = numericPayloads.numRoundArray,
      maxBinsArray = numericPayloads.maxBinsArray,
      trainTestRatioArray = numericPayloads.trainTestRatioArray
    )

    val permutationCollection = xgboostConfigGenerator(fullPermutationConfig)

    randomSampleArray(permutationCollection, countTarget, seed)

  }

  def convertXGBoostResultToConfig(predictionDataFrame: DataFrame): Array[XGBoostConfig] = {

    val collectionBuffer = new ArrayBuffer[XGBoostConfig]()

    val dataCollection = predictionDataFrame
      .select(getCaseClassNames[XGBoostConfig] map col :_*)
      .collect()

    dataCollection.foreach{ x =>
      collectionBuffer += XGBoostConfig(
        alpha = x(0).toString.toDouble,
        eta = x(1).toString.toDouble,
        gamma = x(2).toString.toDouble,
        lambda = x(3).toString.toDouble,
        maxDepth = x(4).toString.toInt,
        subSample = x(5).toString.toDouble,
        minChildWeight = x(6).toString.toDouble,
        numRound = x(7).toString.toInt,
        maxBins = x(8).toString.toInt,
        trainTestRatio = x(9).toString.toDouble
      )
    }
    collectionBuffer.result.toArray
  }

  // MULTILAYER PERCEPTRON CLASSIFIER METHODS
  //TODO: figure this out.

}
