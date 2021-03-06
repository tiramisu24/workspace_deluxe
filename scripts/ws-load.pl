#!/usr/bin/env perl
########################################################################
# adpated for WS 0.1.0+ by Michael Sneddon, LBL
# Original authors: Christopher Henry, Scott Devoid, Paul Frybarger
# Contact email: mwsneddon@lbl.gov or chenry@mcs.anl.gov
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace printObjectInfo);

my $fullCommand = "ws-load ";
foreach my $arg (@ARGV) {
	$fullCommand .= " ".$arg;
}

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Object type","Object Name","Filename or data"];
my $servercommand = "save_objects";
my $translation = {
	"Object Name" => "id",
	"Object type" => "type",
	workspace => "workspace",
	command => "command"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-load <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w=s', 'Name of workspace', {"default" => workspace()} ],
    [ 'metadata|m:s', 'Filename with meta data to associate with the object' ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
my $usageHead = "\nNAME\n  ws-load -- developer tool for loading a JSON object to a workspace\n\n";
$usageHead .= "  WARNING! This command is designed for developers who understand the underlying\n";
$usageHead .= "  data type definitions and how to encode KBase data types as JSON documents.\n";
$usageHead .= "  If you just want to upload your own data (for instance, a GenBank file), you\n";
$usageHead .= "  should use the standard front-end data uploader tools from https://kbase.us\n";
$usage = $usageHead."\nSYNOPSIS\n  ".$usage;
$usage .= "\nDESCRIPTION\n";
$usage .= "    Load data in JSON format to a workspace.  If data is in a file, the filename\n";
$usage .= "    should be provided.  Otherwise, data in JSON format can be entered directly into\n";
$usage .= "    the command line.  Note that the Workspace only supports saving typed object \n";
$usage .= "    data (that is, types defined in KIDL as a typedef structure) even if a non-object\n";
$usage .= "    type is released.  For instance, if a list type is released, you cannot save\n";
$usage .= "    instances of that list type to the Workspace.\n\n";
$usage .= "    If you want to set user meta data, the meta data should also be provided in JSON\n";
$usage .= "    format as an object with string keys and string values. The meta data can be in a\n";
$usage .= "    file or specified directly via the command line.  If an object definition\n";
$usage .= "    specifies automatic metadata extraction with the \@meta ws annotation, and your\n";
$usage .= "    metadata name conflicts, then your metadata will be silently overwritten.\n";
$usage .= "\n";
if (defined($opt->{help})) {
	print $usage;
    exit;
}

#Processing primary arguments
if (scalar(@ARGV) > scalar(@{$primaryArgs})) {
	print STDERR "Too many input arguments given.  Run with -h or --help for usage information\n";
	exit 1;
}
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print STDERR "Not enough input arguments provided.  Run with -h or --help for usage information\n";
		exit 1;
	}
}
#Instantiating parameters
my $params = {
};
foreach my $key (keys(%{$translation})) {
	if (defined($opt->{$key})) {
		$params->{$translation->{$key}} = $opt->{$key};
	}
}
#Handling data
#if (!defined($opt->{stringdata}) || $opt->{stringdata} != 1) {
#	$params->{json} = 1;
#}
if (-e $opt->{"Filename or data"}) {
	open(my $fh, "<", $opt->{"Filename or data"}) || return;
   	$params->{data} = "";
    while (my $line = <$fh>) {
    	$params->{data} .= $line;
    }
    close($fh);
} else {
	$params->{data} = $opt->{"Filename or data"};
}

# parse object as json
my $json_parser = JSON->new->allow_nonref->pretty;
eval {
	$params->{data} = $json_parser->decode($params->{data});
};
if($@) {
	print "Object could not be saved!  Data was not a valid JSON document!\n";
	print STDERR $@."\n";
	exit 1;
}

if (defined($opt->{metadata})) {
	if (-e $opt->{metadata}) {
		open(my $fh, "<", $opt->{metadata}) || return;
	   	$params->{metadata} = "";
	    while (my $line = <$fh>) {
	    	$params->{metadata} .= $line;
	    }
	    close($fh);
	} else {
		$params->{metadata} = $opt->{metadata};
	}
	eval {
		$params->{metadata} = $json_parser->decode($params->{metadata});
	};
	if($@) {
		print "Object could not be saved!  Meta data was not a valid JSON document!\n";
		print STDERR $@."\n";
		exit 1;
	}
}

#lookup version number of WS Service that will be loading the data
my $ws_ver = '';
if ($opt->{showerror} == 0){
	eval { $ws_ver = $serv->ver(); };
	if($@) {
		print "Object could not be saved! Error connecting to the WS server.\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else{
	$ws_ver = $serv->ver();
}

# set provenance info
my $PA = {
		"service"=>"Workspace",
		"service_ver"=>$ws_ver,
		"script"=>"ws-load",
		"script_command_line"=>$fullCommand
	  };
$params->{provenance} = [ $PA ];


# setup the new save_objects parameters
my $saveObjectsParams = {
		"objects" => [
			   {
				"data"  => $params->{data},
				"name"  => $params->{id},
				"type"  => $params->{type},
				"meta"  => $params->{metadata},
				"provenance" => $params->{provenance}
			   }
			]
	};
if ($params->{workspace} =~ /^\d+$/ ) { #is ID
	$saveObjectsParams->{id}=$opt->{workspace}+0;
} else { #is name
	$saveObjectsParams->{workspace}=$opt->{workspace};
}

#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($saveObjectsParams); };
	if($@) {
		print "Object could not be saved!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else{
	$output = $serv->$servercommand($saveObjectsParams);
}

#Report the results
print "Object saved.  Details:\n";
if (scalar(@$output)>0) {
	foreach my $object_info (@$output) {
		printObjectInfo($object_info);
	}
} else {
	print "No details returned!\n";
}
print "\n";

exit 0;