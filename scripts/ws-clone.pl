#!/usr/bin/env perl
########################################################################
# Authors: Christopher Henry, Scott Devoid, Paul Frybarger
# Contact email: chenry@mcs.anl.gov
# Development location: Mathematics and Computer Science Division, Argonne National Lab
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseWorkspaceInfo);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Destination workspace name"];
my $servercommand = "clone_workspace";
my $translation = {
	"globalread"=>"globalread",
	"description"=>"description"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-clone <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w:s', 'Name of the workspace to clone', {"default" => workspace()} ],
    [ 'description|d=s', 'New workspace description (1000 characters max)',{"default"=>''}],
    [ 'globalread|g=s', 'Set global read permissions (r=read,n=none)',{"default"=>'n'}],
    [ 'showerror|e', 'Show any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
if (defined($opt->{help})) {
	print $usage;
	exit;
}
#Processing primary arguments
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print $usage;
		exit;
	}
}

#Instantiating parameters
my $params = {
	      wsi => { workspace=>$opt->{workspace} },
	      workspace => $opt->{"Destination workspace name"}
	      };
foreach my $key (keys(%{$translation})) {
	if (defined($opt->{$key})) {
		$params->{$translation->{$key}} = $opt->{$key};
	}
}
#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Workspace could not be cloned!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
    
} else {
	$output = $serv->$servercommand($params);
}

my $obj = parseWorkspaceInfo($output);
print "Workspace with ".$obj->{objects}." objects cloned into: ".$obj->{workspace}." with id: ".$obj->{id}."\n";

exit 0;