process PREPROCESS {
  publishDir "${params.output_dir}", pattern: 'gridss_preprocess', mode: "${params.publish_mode}"

  input:
  tuple val(meta), path(tumour_bam), path(normal_bam)
  path(ref_data_genome_dir)
  val(ref_data_genome_fn)

  output:
  tuple val(meta), path('gridss_preprocess/')

  script:
  """
  gridss \
    --jvmheap "${params.gridss_jvmheap}" \
    --jar "${params.gridss_jar}" \
    --steps preprocess \
    --reference "${ref_data_genome_dir}/${ref_data_genome_fn}" \
    --workingdir gridss_preprocess/ \
    --threads 4 \
    "${normal_bam}" \
    "${tumour_bam}"
  """
}