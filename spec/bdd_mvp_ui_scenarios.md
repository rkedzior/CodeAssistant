# BDD — MVP UI Scenarios

```gherkin
Feature: Project setup and configuration
  As a developer
  I want to configure my repository and OpenAI
  So that I can index, search and analyze the codebase

  Scenario: Configure project with local git repository path
    Given I open the Project Setup page
    When I enter a valid local repository path
      And I enter a valid OpenAI API key
      And I click "Save configuration"
    Then I should see a success message
      And the Dashboard should show "Configured"

  Scenario: Configure project with GitHub repository
    Given I open the Project Setup page
    When I select "GitHub repository"
      And I enter a valid GitHub repository identifier
      And I enter a valid GitHub token
      And I enter a valid OpenAI API key
      And I click "Save configuration"
    Then I should see a success message

Feature: Indexing the repository
  As a developer
  I want to index and update to a specific commit
  So that search and analysis use the correct code state

  Scenario: Run initial index using main HEAD
    Given the project is configured
      And no index exists yet
      And I open the Indexing page
    When I click "Initial index"
      And I confirm the action
    Then an indexing job should start
      And I should see job progress
    When the job completes successfully
    Then the last indexed commit should be displayed

  Scenario: Update index to a target commit hash
    Given the project is indexed at commit "A"
      And I open the Indexing page
    When I enter a commit hash "X"
      And I click "Update index to commit"
      And I confirm the action
    Then an update job should start from commit "A" to commit "X"
    When the job completes successfully
    Then the last indexed commit should be "X"

  Scenario: Full reload index at a target commit hash
    Given the project is indexed at commit "A"
      And I open the Indexing page
    When I enter a commit hash "X"
      And I click "Full reload"
      And I confirm the action
    Then a full indexing job should start
    When the job completes successfully
    Then the last indexed commit should be "X"

  Scenario: Indexing excludes gitignored files
    Given the project is configured
      And the repository contains a file ignored by .gitignore
      And I open the Indexing page
    When I run "Initial index"
    Then the ignored file should not appear in indexed file counts
      And the ignored file should not appear in search results

Feature: Text search (lexical)
  As a developer
  I want fast exact and simple regex search
  So that I can locate files and strings quickly

  Scenario: Exact text search returns matching files
    Given the repository is indexed
      And I open the Search page
      And I select "Text search"
    When I enter the query "MyClass"
      And I click "Search"
    Then I should see a list of matching files with previews

  Scenario: Simple regex search returns matching files
    Given the repository is indexed
      And I open the Search page
      And I select "Text search"
    When I enable "Regex"
      And I enter the query "foo\s+bar"
      And I click "Search"
    Then I should see matching files and previews

  Scenario: Invalid regex shows a validation error
    Given the repository is indexed
      And I open the Search page
      And I select "Text search"
    When I enable "Regex"
      And I enter the query "foo("
      And I click "Search"
    Then I should see an error message indicating the regex is invalid
      And no results should be shown

Feature: Semantic search (vector)
  As a developer
  I want semantic search
  So that I can find relevant code by meaning

  Scenario: Semantic search returns relevant results
    Given the repository is indexed with vector embeddings
      And I open the Search page
      And I select "Semantic search"
    When I enter the query "where is authentication token validated"
      And I click "Search"
    Then I should see top results ranked by relevance

  Scenario: Semantic search filtered to specification documents
    Given the repository is indexed with vector embeddings
      And I open the Search page
      And I select "Semantic search"
    When I set filter Type to "documentation"
      And I set filter Subtype to "spec"
      And I enter the query "what are the non-functional requirements"
      And I click "Search"
    Then I should see results only from /spec Markdown files

Feature: File viewer
  As a developer
  I want to view files at the indexed commit
  So that I can inspect results and add context for analysis

  Scenario: Open file from search results
    Given the repository is indexed
      And I have performed a search that returned results
    When I click on a file result
    Then I should see the file content
      And I should see the current indexed commit hash displayed

Feature: Analysis workspace
  As a developer
  I want retrieval-assisted free-form explanations
  So that I can understand the codebase faster

  Scenario: Run analysis with code scope enabled
    Given the repository is indexed with vector embeddings
      And I open the Analysis workspace
    When I enter the prompt "Explain the request validation flow"
      And I click "Analyze"
    Then I should see retrieved context items
      And I should see a free-form answer

  Scenario: Analysis shows error when OpenAI request fails
    Given the repository is indexed with vector embeddings
      And I open the Analysis workspace
    When I enter a prompt
      And the OpenAI API request fails
      And I click "Analyze"
    Then I should see an error message indicating the analysis failed
      And I should be able to retry

Feature: Observations and spec updates
  As a developer
  I want to store observations and update /spec files
  So that specs stay aligned with development

  Scenario: Add a plain text observation
    Given I open the Observations page
    When I paste observation text
      And I click "Save observation"
    Then I should see the observation in the list

  Scenario: Search observations by keyword
    Given I have saved observations
      And I open the Observations page
    When I search for "refactor"
    Then I should see only observations that contain "refactor"

  Scenario: Propose and apply spec updates from observations
    Given I have saved observations
      And the repository contains Markdown files under /spec
      And I open the Spec Manager page
    When I click "Propose updates"
    Then I should see suggested changes grouped by spec file
    When I accept the suggested changes
      And I click "Apply updates"
    Then the spec files in /spec should be updated in the working tree
      And I should see a message indicating changes are ready to be committed

Feature: MCP (local)
  As a developer
  I want MCP tools for search and writing observations
  So that local agents can integrate with the system

  Scenario: View MCP server status and available tools
    Given I open the MCP Status page
    Then I should see the MCP server status
      And I should see the tools "search" and "write_observation"

  Scenario: Write an observation via MCP and see it in the UI
    Given the MCP server is running
      And I open the MCP Status page
    When I submit observation text via the MCP "write_observation" test action
    Then I should see a success result
      And the observation should appear in the Observations page

Feature: End-to-end MVP happy path
  As a developer
  I want a smooth workflow from indexing to spec updates
  So that the tool becomes part of my daily development

  Scenario: Index, search, analyze, capture observation, update spec
    Given I have configured the project
    When I run "Initial index" on main HEAD
    Then indexing should complete successfully

    When I perform a text search for "PaymentService"
    Then I should see matching files

    When I ask "Explain payment capture flow" in the Analysis workspace
    Then I should see a free-form explanation

    When I save an observation about missing idempotency handling
    Then the observation should be stored

    When I propose spec updates from recent observations and apply accepted changes
    Then /spec Markdown files should be updated in the working tree
```

