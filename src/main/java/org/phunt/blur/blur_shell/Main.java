/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.phunt.blur.blur_shell;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jline.console.ConsoleReader;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.phunt.blur.blur_shell.Main.QuitCommand.QuitCommandException;

import com.google.common.collect.ImmutableMap;
import com.nearinfinity.blur.thrift.generated.Blur.Client;
import com.nearinfinity.blur.thrift.generated.BlurException;

public class Main {
  static boolean debug;

  private static Map<String, Command> commands;
  
  public static void usage() {
    System.out.println("Usage: java " + Main.class.getName()
        + " controller:port");
  }

  private static class DebugCommand extends Command {

    @Override
    public void doit(PrintWriter out, Client client, String[] args)
        throws CommandException, TException, BlurException {
      if (debug == true) {
        debug = false;
      } else {
        debug = true;
      }
      out.println("debugging is now " + (debug ? "on" : "off"));
    }

    @Override
    public String help() {
      return "toggle debugging on/off";
    }
    
  }

  private static class HelpCommand extends Command {
    @Override
    public void doit(PrintWriter out, Client client, String[] args)
        throws CommandException, TException, BlurException {
      out.println("Available commands:");
      for (Entry<String, Command> e: commands.entrySet()) {
        out.println("  " + e.getKey() + " - " + e.getValue().help());
      }
    }

    @Override
    public String help() {
      return "display help";
    }
  }

  public static class QuitCommand extends Command {
    @SuppressWarnings("serial")
    public static class QuitCommandException extends CommandException {
      public QuitCommandException() {
        super("quit");
      }
    }

    @Override
    public void doit(PrintWriter out, Client client, String[] args)
        throws CommandException, TException, BlurException {
      throw new QuitCommandException();
    }

    @Override
    public String help() {
      return "exit the shell";
    }
  }

  public static void main(String[] args) throws Throwable {
    commands = new ImmutableMap.Builder<String,Command>()
        .put("help", new HelpCommand())
        .put("debug", new DebugCommand())
        .put("quit", new QuitCommand())
        .put("listtables", new ListTablesCommand())
        .put("createtable", new CreateTableCommand())
        .put("tablestats", new TableStatsCommand())
        .build();

    try {
      ConsoleReader reader = new ConsoleReader();

      reader.setPrompt("blur> ");

      if ((args == null) || (args.length != 1)) {
        usage();
        return;
      }

      String[] hostport = args[0].split(":"); 

      if (hostport.length != 2) {
        usage();
        return;
      }

      List<Completer> completors = new LinkedList<Completer>();

      completors.add(new StringsCompleter(commands.keySet()));
      completors.add(new FileNameCompleter());

      for (Completer c : completors) {
        reader.addCompleter(c);
      }

      TTransport trans = new TSocket(hostport[0], Integer.parseInt(hostport[1]));
      TProtocol proto = new TBinaryProtocol(new TFramedTransport(trans));
      Client client = new Client(proto);
      try {
          trans.open();

          String line;
          PrintWriter out = new PrintWriter(reader.getOutput());
          while ((line = reader.readLine()) != null) {
            String[] commandArgs = line.split("\\s");
            Command command = commands.get(commandArgs[0]);
            if (command == null) {
              out.println("unknown command \"" + commandArgs[0] + "\"");
            } else {
              command.doit(out, client, commandArgs);
            }
          }
      } catch (QuitCommandException e) {
        // exit gracefully
      } finally {
          trans.close();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw t;
    }
  }

}
