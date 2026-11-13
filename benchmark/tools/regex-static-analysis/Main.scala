package redos_experiment

import analysis.AnalysisSettings.NFAConstruction
import analysis.AnalysisSettings.PriorityRemovalStrategy
import analysis.ExploitString
import analysis.NFAAnalyserFlattening
import analysis.NFAAnalyserInterface.AnalysisResultsType
import analysis.NFAAnalyserInterface.IdaAnalysisResultsIda
import regexcompiler.MyPattern

object Main extends Wrapper {

  val analyser = new NFAAnalyserFlattening(PriorityRemovalStrategy.UNPRIORITISE)

  def check(source: String, flags: String): ujson.Obj = {
    val graph = MyPattern.toNFAGraph(source, NFAConstruction.JAVA)
    analyser.containsEDA(graph) match {
      case AnalysisResultsType.EDA =>
        vulnerable(exponential, attack(analyser.findEDAExploitString(graph)))
      case AnalysisResultsType.NO_EDA =>
        analyser.containsIDA(graph) match {
          case AnalysisResultsType.IDA =>
            // The degree of the analysis is the degree of ambiguity, which is one less than the degree of the time.
            val degree = analyser.getIdaAnalysisResults(graph).asInstanceOf[IdaAnalysisResultsIda].getDegree
            vulnerable(polynomial(degree + 1), attack(analyser.findIDAExploitString(graph)))
          case AnalysisResultsType.NO_IDA => safe
          case result                     => error(s"IDA analysis: $result")
        }
      case result => error(s"EDA analysis: $result")
    }
  }

  def attack(exploit: ExploitString): ujson.Value =
    // An exploit string of EDA has one pump with the degree 0.
    if (exploit.getDegree == 0) pumps(Seq(exploit.getPrefix), Seq(exploit.getPumpByDegree(0)), exploit.getSuffix)
    else pumps(exploit.getSeparators.toSeq, exploit.getPumps.toSeq, exploit.getSuffix)
}
