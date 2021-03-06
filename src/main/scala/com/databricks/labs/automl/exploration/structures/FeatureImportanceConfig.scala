package com.databricks.labs.automl.exploration.structures

case class FeatureImportanceConfig(
  labelCol: String,
  featuresCol: String,
  numericBoundaries: Map[String, (Double, Double)],
  stringBoundaries: Map[String, List[String]],
  scoringMetric: String,
  trainPortion: Double,
  trainSplitMethod: String,
  trainSplitChronologicalColumn: String,
  trainSplitChronlogicalRandomPercentage: Double,
  parallelism: Int,
  kFold: Int,
  seed: Long,
  scoringOptimizationStrategy: String,
  firstGenerationGenePool: Int,
  numberOfGenerations: Int,
  numberOfMutationsPerGeneration: Int,
  numberOfParentsToRetain: Int,
  geneticMixing: Double,
  generationalMutationStrategy: String,
  mutationMagnitudeMode: String,
  fixedMutationValue: Int,
  autoStoppingScore: Double,
  autoStoppingFlag: Boolean,
  evolutionStrategy: String,
  continuousEvolutionMaxIterations: Int,
  continuousEvolutionStoppingScore: Double,
  continuousEvolutionParallelism: Int,
  continuousEvolutionMutationAggressiveness: Int,
  continuousEvolutionGeneticMixing: Double,
  continuousEvolutionRollingImprovementCount: Int,
  dataReductionFactor: Double,
  firstGenMode: String,
  firstGenPermutations: Int,
  firstGenIndexMixingMode: String,
  firstGenArraySeed: Long,
  fieldsToIgnore: Array[String],
  numericFillStat: String,
  characterFillStat: String,
  modelSelectionDistinctThreshold: Int,
  dateTimeConversionType: String,
  modelType: String,
  featureImportanceModelFamily: String
)
