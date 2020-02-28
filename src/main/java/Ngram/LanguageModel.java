package Ngram;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.*;

public class LanguageModel {

    public static class Map extends Mapper<LongWritable, Text, Text, Text> {

        //input: I love big data\t 10
        //output: key = I love big
        // value = data
        int threshold;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            threshold = conf.getInt("threshold", 20);
        }

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            //I love big data\t10
            String line = value.toString().trim();

            String[] wordsPlusCount = line.split("\t");
            if(wordsPlusCount.length<2){
                return;
            }

            String[] words = wordsPlusCount[0].split("\\s+");
            int count = Integer.parseInt(wordsPlusCount[1]);

            if(threshold>count) {
                return;
            }

            //{I, love, big, data}
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < words.length - 1; i++){
                sb.append(words[i]);
                sb.append(" ");
            }

            String outputkey = sb.toString().trim();
            String outputValue = words[words.length - 1] + "=" + count;

            context.write(new Text(outputkey), new Text(outputValue));
        }
    }

    public static class Reduce extends Reducer<Text, Text, DBOutputWritable, NullWritable> {

        int topK;
        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            topK = conf.getInt("topK", 5);
        }

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            //key = I love big
            //value <data = 10, girl = 100, boy = 1000...>
            TreeMap<Integer, List<String>> tm = new TreeMap<Integer, List<String>>(Collections.<Integer>reverseOrder());

            for(Text val: values){
                String value = val.toString().trim();
                String word = value.split("=")[0];
                int count = Integer.parseInt(value.split("=")[1].trim());
                if(tm.containsKey(count)){
                    tm.get(count).add(word);
                }else{
                    List<String> list = new ArrayList<>();
                    list.add(word);
                    tm.put(count, list);
                }
            }

            Iterator<Integer> iter = tm.keySet().iterator();
            for(int j = 0; iter.hasNext() && j<topK;){
                int keyCount = iter.next();
                List<String> words = tm.get(keyCount);
                for (int i = 0;i<words.size()&&j<topK;i++){
                    context.write(new DBOutputWritable(key.toString(), words.get(i), keyCount), NullWritable.get());
                    j++;
                }
            }
        }
    }
}
